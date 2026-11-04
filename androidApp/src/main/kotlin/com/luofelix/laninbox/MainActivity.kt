package com.luofelix.laninbox

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.ReceiverStatus
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.DevelopmentMessageSender
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.SecurePairingForm
import com.luofelix.laninbox.feature.send.TransferControls
import com.luofelix.laninbox.feature.send.TransferStatus
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val discoveryEnabled = MutableStateFlow(false)
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        discoveryEnabled.value = allowed
        transfers.networkPermissionChanged(allowed)
    }
    private var developmentSender: DevelopmentMessageSender? = null
    private val transfers by viewModels<AndroidTransferViewModel>()
    private val chooseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) transfers.choose(it)
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        transfers.completeExport(it)
    }
    private val scanQr = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        val invitation = PairingInvitation.decode(contents)
        if (invitation == null) {
            transfers.invalidInvitation()
        } else if (canAccessLan()) {
            transfers.pair(invitation.address, invitation.fingerprint, invitation.code)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            androidLogger.info("Android activity started")
        }

        // Match the debug manifest's cleartext policy when wiring the development sender.
        val developmentMode = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        val sender = if (developmentMode) {
            val client = DevelopmentMessageSender()
            developmentSender = client
            MessageSender { address, message ->
                if (!canAccessLan()) {
                    // Leave the draft pending so the user can retry after granting access.
                    DeliveryResult.PermissionRequired
                } else {
                    client.send(address, message)
                }
            }
        } else {
            null
        }
        val senderId = AndroidSettings(applicationContext).senderId()
        val initialDraft = if (intent.action == Intent.ACTION_SEND &&
            intent.type == "text/plain"
        ) {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        } else {
            ""
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { transfers.receiveInForeground() }
                launch {
                    discoveryEnabled.collectLatest { enabled ->
                        if (enabled) discoverDesktops(this@MainActivity).collect(transfers::discoveryChanged)
                    }
                }
            }
        }
        setContent {
            val connected by transfers.client.collectAsStateWithLifecycle()
            val pairing by transfers.pairing.collectAsStateWithLifecycle()
            val devices by transfers.devices.collectAsStateWithLifecycle()
            val remembered by transfers.remembered.collectAsStateWithLifecycle()
            val outgoing by transfers.outgoing.collectAsStateWithLifecycle()
            val incoming by transfers.incoming.collectAsStateWithLifecycle()
            val inbox by transfers.inbox.collectAsStateWithLifecycle()
            App(
                sender = connected?.let { connection -> MessageSender { _, message -> connection.sendMessage(message) } }
                    ?: sender,
                senderId = senderId,
                initialDraft = initialDraft,
                pairedAddress = connected?.address,
                receiverStatus = if (connected == null) ReceiverStatus.Disabled else ReceiverStatus.Foreground,
                messages = inbox.mapNotNull { it.message?.let(::InboxMessage) },
                attachments = inbox.mapNotNull { it.attachment },
                previewAttachment = transfers.files::preview,
                exportAttachment = ::exportAttachment,
                deleteMessage = { transfers.deleteHistory(it.request.messageId) },
                deleteAttachment = { transfers.deleteHistory(it.id) },
                clearHistory = { transfers.deleteHistory(null) },
                sendActions = {
                    SecurePairingForm(
                        connected = connected != null,
                        busy = pairing == TransferStatus.Preparing,
                        failed = pairing == TransferStatus.Failed,
                        onPair = { address, fingerprint, invitation ->
                            if (canAccessLan()) transfers.pair(address, fingerprint, invitation)
                            else transfers.permissionRequired()
                        },
                        onDisconnect = transfers::disconnect,
                        devices = devices.map { it.name to it.address },
                        remembered = remembered != null,
                        onDiscover = { if (canAccessLan()) discoveryEnabled.value = true },
                        onScan = {
                            if (canAccessLan()) {
                                scanQr.launch(ScanOptions()
                                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                    .setPrompt(getString(R.string.pairing_scanner_prompt))
                                    .setBeepEnabled(false))
                            }
                        },
                        onReconnect = { if (canAccessLan()) transfers.reconnectSaved(it) },
                    )
                    if (pairing == TransferStatus.PermissionRequired) TransferControls(pairing)
                    if (connected != null) {
                        val busy = outgoing.status == TransferStatus.Preparing || outgoing.status == TransferStatus.Sending
                        TransferControls(
                            outgoing.status, outgoing.progress, outgoing.name,
                            choose = if (busy) null else ({ chooseFile.launch(arrayOf("*/*")) }),
                            cancel = if (busy) transfers::cancelUpload else null,
                            retry = if (outgoing.status == TransferStatus.Failed || outgoing.status == TransferStatus.Canceled)
                                transfers::retryUpload else null,
                        )
                    }
                },
                inboxActions = {
                    val paused = incoming.status == TransferStatus.Canceled || incoming.status == TransferStatus.Failed
                    TransferControls(
                        incoming.status, incoming.progress, incoming.name,
                        cancel = if (connected != null && incoming.status == TransferStatus.Receiving)
                            transfers::pauseReception else null,
                        retry = if (connected != null && paused) transfers::retryReception else null,
                    )
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        val allowed = hasLanPermission()
        discoveryEnabled.value = allowed
        transfers.networkPermissionChanged(allowed)
    }

    private fun exportAttachment(attachment: AttachmentInfo) {
        transfers.selectExport(attachment)
        saveFile.launch(attachment.name)
    }

    private fun canAccessLan(): Boolean {
        if (hasLanPermission()) return true
        localNetworkPermission.launch(LOCAL_NETWORK)
        return false
    }

    private fun hasLanPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 || checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        developmentSender?.close()
        super.onDestroy()
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
