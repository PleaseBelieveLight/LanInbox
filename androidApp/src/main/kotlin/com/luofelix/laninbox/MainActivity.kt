package com.luofelix.laninbox

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.CameraPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.feature.inbox.InboxMessage
import com.luofelix.laninbox.feature.inbox.InboxOperationError
import com.luofelix.laninbox.feature.inbox.viewerMediaType
import com.luofelix.laninbox.feature.send.DeliveryResult
import com.luofelix.laninbox.feature.send.DevelopmentMessageSender
import com.luofelix.laninbox.feature.send.MessageSender
import com.luofelix.laninbox.feature.send.TransferFeedback
import com.luofelix.laninbox.feature.send.TransferStatus
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

class MainActivity : ComponentActivity() {
    private val discoveryEnabled = MutableStateFlow(false)
    private val cameraAllowed = MutableStateFlow(false)
    private var networkRequested = false
    private val localNetworkPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed ->
        discoveryEnabled.value = allowed
        transfers.networkPermissionChanged(allowed)
    }
    private val cameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { allowed -> cameraAllowed.value = allowed }
    private var developmentSender: DevelopmentMessageSender? = null
    private val transfers by viewModels<AndroidTransferViewModel>()
    private val chooseImage = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) {
        if (it != null) transfers.choose(it)
    }
    private val chooseFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        if (it != null) transfers.choose(it)
    }
    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        transfers.completeExport(it)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 29) {
            // The screen surfaces provide contrast behind three-button navigation.
            window.isNavigationBarContrastEnforced = false
        }
        if (savedInstanceState == null) androidLogger.info("Android activity started")
        networkRequested = savedInstanceState?.getBoolean("networkRequested") ?: false

        // The debug-only sender follows the cleartext policy in the debug manifest.
        val development = if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val client = DevelopmentMessageSender()
            developmentSender = client
            MessageSender { address, message ->

                if (hasLanPermission()) client.send(address, message)
                else DeliveryResult.PermissionRequired
            }
        } else null
        val senderId = AndroidSettings(applicationContext).senderId()
        val initialDraft = if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        } else ""

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
            Home(senderId, initialDraft, development)
        }
    }

    @Composable
    private fun Home(senderId: String, initialDraft: String, development: MessageSender?) {
        val connection by transfers.client.collectAsStateWithLifecycle()
        val pairing by transfers.pairing.collectAsStateWithLifecycle()
        val invitationRejected by transfers.invitationRejected.collectAsStateWithLifecycle()
        val devices by transfers.devices.collectAsStateWithLifecycle()
        val remembered by transfers.remembered.collectAsStateWithLifecycle()
        val authorized by discoveryEnabled.collectAsStateWithLifecycle()
        val selectedFile by transfers.selectedFile.collectAsStateWithLifecycle()
        val outgoing by transfers.outgoing.collectAsStateWithLifecycle()
        val incoming by transfers.incoming.collectAsStateWithLifecycle()
        val inbox by transfers.inbox.collectAsStateWithLifecycle()
        val attachmentReceivedAt by transfers.files.receivedAt.collectAsStateWithLifecycle()
        val locale = Locale.getDefault()
        val timeZone = TimeZone.getDefault()
        val receivedDateLabels = remember(attachmentReceivedAt, locale, timeZone.id) {
            attachmentReceivedAt.mapValues { formatReceivedDateTime(it.value, locale, timeZone) }
        }
        val inboxFailure by transfers.inboxOperationFailed.collectAsStateWithLifecycle()
        var showConnection by rememberSaveable { mutableStateOf(false) }
        var scanning by rememberSaveable { mutableStateOf(false) }
        var developmentAddress by rememberSaveable { mutableStateOf<String?>(null) }

        val client = connection
        val savedDevice = remembered
        val pairingBusy = pairing == TransferStatus.Preparing
        val firstConnection = savedDevice == null && client == null && developmentAddress == null
        val computerName = devices.firstOrNull { it.fingerprint == (client?.device ?: savedDevice)?.fingerprint }?.name
            ?: stringResource(Res.string.computer)
        fun beginScan() {
            transfers.clearPairingFeedback()
            showConnection = false
            scanning = true
        }
        fun connect() {
            if (firstConnection && !pairingBusy) beginScan()
            else showConnection = true
        }

        LaunchedEffect(client) {
            if (client != null) {
                scanning = false
                showConnection = false
            }
        }
        if (scanning && client == null) {
            ScanPage(
                busy = pairingBusy,
                failed = pairing == TransferStatus.Failed,
                invitationRejected = invitationRejected,
                authorized = authorized,
                active = !showConnection,
                onBack = { scanning = false },
                onDetails = { showConnection = true },
            )
        } else {
            val secureSender = remember(client) {
                client?.let { paired -> MessageSender { _, message -> paired.sendMessage(message) } }
            }
            val openWithLabel = stringResource(Res.string.attachment_open_with)
            AndroidHomeScreen(
                messages = inbox.mapNotNull { it.message?.let(::InboxMessage) },
                attachments = inbox.mapNotNull { it.attachment },
                attachmentReceivedDates = receivedDateLabels,
                previewAttachment = transfers.files::preview,
                exportAttachment = ::exportAttachment,
                openAttachment = { openAttachment(it, openWithLabel) },
                localAttachmentPreview = { attachment, onUnavailable ->
                    AndroidLocalAttachmentPreview(
                        attachment = attachment,
                        loadUri = { transfers.files.openUri(attachment) },
                        loadText = { transfers.files.textPreview(attachment) },
                        onUnavailable = onUnavailable,
                    )
                },
                deleteMessage = { transfers.deleteHistory(it.request.messageId) },
                deleteAttachment = { transfers.deleteHistory(it.id) },
                clearHistory = { transfers.deleteHistory(null) },
                device = {
                    val label = when {
                        client != null -> computerName
                        pairingBusy -> stringResource(Res.string.connecting)
                        savedDevice != null -> stringResource(Res.string.device_offline_name, computerName)
                        developmentAddress != null -> stringResource(Res.string.connect_computer)
                        else -> stringResource(Res.string.scan_to_connect)
                    }
                    AndroidDeviceButton(label, scan = firstConnection && !pairingBusy) { connect() }
                },
                inboxStatus = {
                    if (inboxFailure) InboxOperationError()
                    val paused = incoming.status == TransferStatus.Canceled || incoming.status == TransferStatus.Failed
                    if (incoming.status != TransferStatus.Delivered) {
                        TransferFeedback(
                            incoming.status, incoming.progress,
                            cancel = transfers::pauseReception.takeIf { client != null && incoming.status == TransferStatus.Receiving },
                            retry = transfers::retryReception.takeIf { client != null && paused },
                        )
                    }
                },
                composer = {
                    AndroidSendComposer(
                        senderId = senderId,
                        initialDraft = initialDraft,
                        sender = secureSender ?: development.takeIf { authorized },
                        pairedAddress = client?.address,
                        peerIdentity = client?.device?.fingerprint,
                        developmentAddress = developmentAddress,
                        file = selectedFile,
                        outgoingStatus = outgoing.status,
                        outgoingProgress = outgoing.progress,
                        onPickImage = {
                            chooseImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onPickDocument = { chooseFile.launch(DOCUMENT_MIME_TYPES) },
                        onPickFile = { chooseFile.launch(arrayOf("*/*")) },
                        onRemove = transfers::removeSelection,
                        onSendFile = transfers::retryUpload,
                        onCancelFile = transfers::cancelUpload,
                        onConnect = ::connect,
                    )
                },
            )
        }
        if (showConnection) {
            AndroidConnectionSheet(
                deviceName = computerName,
                paired = client != null,
                remembered = savedDevice != null,
                authorized = authorized,
                status = pairing,
                invitationRejected = invitationRejected,
                knownAddress = savedDevice?.address ?: devices.singleOrNull()?.address,
                developmentAddress = developmentAddress,
                onDevelopmentAddress = if (development != null) {
                    { address -> developmentAddress = address.takeIf { it.isNotBlank() } }
                } else null,
                onAuthorize = ::requestLanAccess,
                onScan = ::beginScan,
                onReconnect = transfers::reconnectSaved,
                onPair = transfers::pair,
                onForget = { transfers.disconnect(); showConnection = false },
                onClose = { showConnection = false },
            )
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Composable
    private fun ScanPage(
        busy: Boolean,
        failed: Boolean,
        invitationRejected: Boolean,
        authorized: Boolean,
        active: Boolean,
        onBack: () -> Unit,
        onDetails: () -> Unit,
    ) {
        val allowed by cameraAllowed.collectAsStateWithLifecycle()
        var requested by rememberSaveable { mutableStateOf(false) }
        var cameraFailed by remember { mutableStateOf(false) }
        var invalidCode by remember { mutableStateOf(false) }
        var lastCode by remember { mutableStateOf<String?>(null) }
        BackHandler(enabled = active, onBack = onBack)
        DisposableEffect(Unit) {
            val previous = requestedOrientation
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            onDispose { requestedOrientation = previous }
        }
        LaunchedEffect(authorized, active) {
            if (authorized && active && !allowed && !requested) {
                requested = true
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
        val instruction = stringResource(
            when {
                !authorized -> Res.string.delivery_permission
                !allowed -> Res.string.pairing_camera_permission
                cameraFailed -> Res.string.pairing_camera_failed
                busy -> Res.string.connecting
                invitationRejected -> Res.string.pairing_scan_refresh
                failed -> Res.string.secure_pair_failed
                invalidCode -> Res.string.pairing_scan_invalid
                else -> Res.string.pairing_scan_target
            },
        )
        val action = when {
            !authorized -> Res.string.pairing_allow_network
            !allowed && requested && !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> Res.string.pairing_settings
            !allowed -> Res.string.pairing_allow_network
            cameraFailed || (failed && !invitationRejected) -> Res.string.attachment_retry
            else -> null
        }
        AndroidPairingScanScreen(
            instruction = instruction,
            onBack = onBack,
            onDetails = onDetails,
            busy = busy,
            actionLabel = action?.let { stringResource(it) },
            onAction = {
                when {
                    !authorized -> requestLanAccess()
                    action == Res.string.pairing_settings -> openPermissionSettings()
                    !allowed -> cameraPermission.launch(Manifest.permission.CAMERA)
                    else -> {
                        cameraFailed = false
                        invalidCode = false
                        lastCode = null
                        transfers.clearPairingFeedback()
                    }
                }
            },
            preview = if (allowed && authorized && active && !cameraFailed) {
                { modifier ->
                    ScannerPreview(
                        modifier = modifier,
                        onCameraError = {
                            androidLogger.warn("Pairing camera failed", "type" to it.javaClass.simpleName)
                            cameraFailed = true
                        },
                        onDecoded = { value ->
                            if (transfers.pairing.value != TransferStatus.Preparing && value != lastCode) {
                                val invitation = PairingInvitation.decode(value)
                                invalidCode = invitation == null
                                if (invitation != null) {
                                    lastCode = value
                                    transfers.pair(invitation.address, invitation.fingerprint, invitation.code)
                                }
                            }
                        },
                    )
                }
            } else null,
        )
    }

    override fun onResume() {
        super.onResume()
        val allowed = hasLanPermission()
        discoveryEnabled.value = allowed
        transfers.networkPermissionChanged(allowed)
        cameraAllowed.value = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("networkRequested", networkRequested)
        super.onSaveInstanceState(outState)
    }

    private fun exportAttachment(attachment: AttachmentInfo) {
        transfers.selectExport(attachment)
        saveFile.launch(attachment.name)
    }

    private suspend fun openAttachment(attachment: AttachmentInfo, chooserTitle: String): Boolean {
        return try {
            val uri = transfers.files.openUri(attachment)
            val mediaType = attachment.viewerMediaType().takeUnless { it == "application/octet-stream" } ?: "*/*"
            val viewer = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mediaType)
                // Grant only this received payload, for the lifetime of the selected viewer's task.
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri("attachment", uri)
            }
            startActivity(Intent.createChooser(viewer, chooserTitle))
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            androidLogger.warn("Attachment viewer failed", "attachmentId" to attachment.id, "type" to error.javaClass.simpleName)
            false
        }
    }

    private fun requestLanAccess() {
        if (hasLanPermission()) return
        if (networkRequested && !shouldShowRequestPermissionRationale(LOCAL_NETWORK)) {
            openPermissionSettings()
        } else {
            networkRequested = true
            localNetworkPermission.launch(LOCAL_NETWORK)
        }
    }

    private fun openPermissionSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun hasLanPermission(): Boolean =
        Build.VERSION.SDK_INT < 37 || checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        developmentSender?.close()
        super.onDestroy()
    }

    private companion object {
        const val LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
        val DOCUMENT_MIME_TYPES = arrayOf(
            "application/pdf",
            "text/*",
            "application/rtf",
            "application/msword",
            "application/vnd.ms-excel",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.text",
            "application/vnd.oasis.opendocument.spreadsheet",
            "application/vnd.oasis.opendocument.presentation",
        )
    }
}

@Composable
private fun ScannerPreview(modifier: Modifier, onCameraError: (Exception) -> Unit, onDecoded: (String) -> Unit) {
    val context = LocalContext.current
    val onResult by rememberUpdatedState(onDecoded)
    val onError by rememberUpdatedState(onCameraError)
    val scanner = remember(context) {
        DecoratedBarcodeView(context).apply {
            statusView.visibility = View.GONE
            viewFinder.setLaserVisibility(false)
            barcodeView.marginFraction = 0.19
            decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            barcodeView.addStateListener(object : CameraPreview.StateListener {
                override fun previewSized() = Unit
                override fun previewStarted() = Unit
                override fun previewStopped() = Unit
                override fun cameraClosed() = Unit
                override fun cameraError(error: Exception) = onError(error)
            })
            decodeContinuous { result -> onResult(result.text) }
        }
    }
    AndroidView(factory = { scanner }, modifier = modifier)
    LifecycleResumeEffect(scanner) {
        scanner.resume()
        onPauseOrDispose { scanner.pause() }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    AndroidHomeScreen()
}
