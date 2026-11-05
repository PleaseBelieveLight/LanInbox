package com.luofelix.laninbox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.core.network.SECURE_PORT
import com.luofelix.laninbox.core.network.privateLanAuthority
import com.luofelix.laninbox.feature.send.SelectedFile
import com.luofelix.laninbox.feature.send.SendComposer
import com.luofelix.laninbox.feature.send.TransferFeedback
import com.luofelix.laninbox.feature.send.TransferStatus
import com.luofelix.laninbox.feature.send.draftKind
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import laninbox.shared.generated.resources.*
import org.jetbrains.compose.resources.stringResource

/** Owns the draft and pending preparation independently of the window layout. */
internal class DesktopTransferState(
    private val scope: CoroutineScope,
    private val pairing: DesktopPairingSession,
    private val outbox: DesktopOutbox,
) {
    private var staging: Job? = null
    private var completedId by mutableStateOf<String?>(null)
    var file by mutableStateOf<Path?>(null)
        private set
    var draft by mutableStateOf("")
        private set
    var asCode by mutableStateOf(false)
        private set
    var preparation by mutableStateOf<TransferStatus?>(null)
        private set
    var showDelivery by mutableStateOf(false)
        private set

    private fun busy(): Boolean = staging?.isActive == true || outbox.delivery.value != null

    fun selectFile(path: Path?) {
        if (busy()) return
        file = path
        resetFeedback()
    }

    fun editDraft(text: String) {
        if (busy()) return
        draft = text
        resetFeedback()
    }

    fun selectCode(selected: Boolean) {
        if (busy()) return
        asCode = selected
        resetFeedback()
    }

    private fun resetFeedback() {
        preparation = null
        showDelivery = false
    }

    fun send() {
        val owner = pairing.activeSession.value ?: return
        if (busy()) return
        val source = file
        if (source == null) {
            preparation = if (outbox.enqueueText(owner, draft, draftKind(draft, asCode))) {
                showDelivery = true
                null
            } else TransferStatus.Failed
            return
        }
        showDelivery = true
        preparation = TransferStatus.Preparing
        staging = scope.launch {
            try {
                preparation = if (outbox.enqueueFile(owner, source)) null else TransferStatus.Failed
            } catch (error: CancellationException) {
                preparation = TransferStatus.Canceled
                throw error
            } catch (_: IOException) {
                preparation = TransferStatus.Failed
            }
        }
    }

    fun complete(id: String) {
        if (completedId == id) return
        completedId = id
        if (!showDelivery) return
        if (file != null) file = null
        else {
            draft = ""
            asCode = false
        }
    }

    fun completing(id: String?): Boolean = id != null && id != completedId && showDelivery

    fun cancel() {
        staging?.cancel()
        outbox.cancel()
        preparation = TransferStatus.Canceled
    }

    fun revoke() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { pairing.revoke() }
                if (busy()) cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                preparation = TransferStatus.Failed
            }
        }
    }
}

@Composable
internal fun DesktopSendComposer(
    window: ComposeWindow,
    receiverReady: Boolean,
    paired: Boolean,
    outbox: DesktopOutbox,
    state: DesktopTransferState,
    logger: AppLogger,
    onConnect: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var choosingFile by remember { mutableStateOf(false) }
    val queued by outbox.delivery.collectAsState()
    val delivered by outbox.delivered.collectAsState()
    val active = queued
    val attachment = active?.delivery?.attachment
    val preparation = state.preparation
    val busy = active != null || preparation == TransferStatus.Preparing || state.completing(delivered)
    val source = state.file
    val size by produceState<Long?>(null, source) {
        value = source?.let { path ->
            withContext(Dispatchers.IO) {
                try {
                    Files.size(path)
                } catch (_: IOException) {
                    null
                }
            }
        }
    }
    LaunchedEffect(delivered) {
        delivered?.let(state::complete)
    }

    SendComposer(
        title = stringResource(if (paired) Res.string.send_to_phone else Res.string.send_prepare),
        draft = state.draft,
        onDraftChange = state::editDraft,
        asCode = state.asCode,
        onCodeChange = state::selectCode,
        file = source?.let { SelectedFile(it.fileName.toString(), size) },
        onChoose = {
            if (!choosingFile) scope.launch {
                choosingFile = true
                try {
                    chooseDesktopPath(window, FileDialog.LOAD, logger)?.let(state::selectFile)
                } finally {
                    choosingFile = false
                }
            }
        },
        onRemove = { state.selectFile(null) },
        onSubmit = if (receiverReady && paired) state::send else onConnect,
        connectRequired = !receiverReady || !paired,
        enabled = !busy && !choosingFile,
        failed = preparation == TransferStatus.Failed,
    ) {
        val status = when {
            active != null && !active.offered -> TransferStatus.Waiting
            active != null -> TransferStatus.Sending
            preparation != null -> preparation
            delivered != null && state.showDelivery -> TransferStatus.Delivered
            else -> null
        }
        val progress = if (active != null && attachment != null) {
            if (attachment.size == 0L) 1f else (active.transferred.toDouble() / attachment.size).toFloat().coerceIn(0f, 1f)
        } else null
        val canCancel = active != null || preparation == TransferStatus.Preparing
        TransferFeedback(status, progress, cancel = state::cancel.takeIf { canCancel })
    }
}

@Composable
internal fun DesktopConnection(
    shown: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    receiverReady: Boolean,
    host: String?,
    fingerprint: String,
    pairedSession: Long?,
    pairing: DesktopPairingSession,
    state: DesktopTransferState,
    deviceName: String,
    onDeviceName: (String) -> Unit,
) {
    val paired = pairedSession != null
    val connectedSession by pairing.connectedSession.collectAsState()
    val connected = receiverReady && paired && connectedSession == pairedSession
    val confirmedPairing by pairing.confirmedPairing.collectAsState()
    var acknowledgedPairing by remember(pairing) { mutableStateOf<Long?>(null) }
    val pairingSucceeded = connected && confirmedPairing != null && confirmedPairing == pairedSession &&
        confirmedPairing != acknowledgedPairing
    OutlinedButton(onClick = onOpen) {
        Text(
            stringResource(
                when {
                    connected -> Res.string.connected_phone
                    paired -> Res.string.paired_phone
                    else -> Res.string.connect_phone
                },
            ),
        )
    }

    val code by pairing.invitation.collectAsState()
    LaunchedEffect(code) {
        code?.let {
            delay(DesktopPairingSession.INVITATION_LIFETIME)
            pairing.expireInvitation(it)
        }
    }
    if (!shown) return

    val address = host?.let { "$it:$SECURE_PORT" }
    val qrAddress = address?.let { privateLanAuthority(it, SECURE_PORT) }
    val invitation = code
    var replacingPairing by remember(pairedSession) { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var confirmingForget by remember { mutableStateOf(false) }
    var name by remember(deviceName) { mutableStateOf(deviceName) }
    LaunchedEffect(receiverReady, address, paired) {
        if (receiverReady && address != null && !paired && pairing.invitation.value == null) pairing.issueInvitation()
    }
    val qr = remember(invitation, qrAddress, fingerprint) {
        if (invitation != null && qrAddress != null) pairingQr(PairingInvitation(qrAddress, fingerprint, invitation))
        else null
    }
    fun closeConnection() {
        if (pairingSucceeded) acknowledgedPairing = confirmedPairing
        onClose()
    }
    AlertDialog(
        onDismissRequest = ::closeConnection,
        title = {
            Text(stringResource(when {
                pairingSucceeded -> Res.string.pairing_success
                connected -> Res.string.connected_phone
                paired -> Res.string.paired_phone
                else -> Res.string.connect_phone
            }))
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (replacingPairing && !pairingSucceeded) Text(stringResource(Res.string.pairing_replacement_hint))
                when {
                    pairingSucceeded -> Text(stringResource(Res.string.pairing_success_detail))
                    paired && !replacingPairing -> {
                        Text(stringResource(if (connected) Res.string.connected_phone_detail else Res.string.paired_phone_detail))
                        Button(
                            enabled = receiverReady && address != null,
                            onClick = {
                                replacingPairing = true
                                pairing.issueInvitation()
                            },
                        ) { Text(stringResource(Res.string.pairing_again)) }
                        if (!receiverReady || address == null) {
                            Text(stringResource(Res.string.pairing_no_address))
                        }
                    }
                    !receiverReady || qrAddress == null -> Text(stringResource(Res.string.pairing_no_address))
                    qr != null -> {
                        Text(stringResource(Res.string.pairing_scan_hint))
                        Image(qr, stringResource(Res.string.pairing_scan_title), Modifier.size(184.dp).align(Alignment.CenterHorizontally))
                    }
                    else -> {
                        Text(stringResource(Res.string.pairing_qr_expired))
                        Button(onClick = pairing::issueInvitation) { Text(stringResource(Res.string.pairing_refresh)) }
                    }
                }
                if (!pairingSucceeded) {
                    TextButton(onClick = { details = !details }) { Text(stringResource(Res.string.pairing_details)) }
                }
                if (details && !pairingSucceeded) {
                    OutlinedTextField(
                        name, { name = it.take(40) }, label = { Text(stringResource(Res.string.device_name)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (name.isNotBlank() && name.trim() != deviceName) {
                        TextButton(onClick = { onDeviceName(name.trim()) }) { Text(stringResource(Res.string.save_device_name)) }
                    }
                    address?.let { value ->
                        SelectionContainer { Text("${stringResource(Res.string.receiver_address)}\n$value") }
                    }
                    if (fingerprint.isNotEmpty()) {
                        SelectionContainer { Text("${stringResource(Res.string.secure_fingerprint)}\n$fingerprint") }
                    }
                    if (!paired || replacingPairing) {
                        invitation?.let { value ->
                            SelectionContainer { Text("${stringResource(Res.string.secure_invitation)}\n$value") }
                        }
                        if (receiverReady && qrAddress == null && address != null) {
                            TextButton(onClick = pairing::issueInvitation) { Text(stringResource(Res.string.pairing_issue_invitation)) }
                        }
                    }
                    if (paired) {
                        TextButton(onClick = { confirmingForget = true }) { Text(stringResource(Res.string.pairing_revoke)) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::closeConnection) {
                Text(stringResource(if (pairingSucceeded) Res.string.pairing_done else Res.string.close))
            }
        },
    )
    if (confirmingForget && !pairingSucceeded) {
        AlertDialog(
            onDismissRequest = { confirmingForget = false },
            title = { Text(stringResource(Res.string.pairing_confirm_forget)) },
            text = { Text(stringResource(Res.string.pairing_confirm_forget_detail)) },
            confirmButton = {
                TextButton(onClick = { confirmingForget = false; state.revoke() }) {
                    Text(stringResource(Res.string.pairing_revoke))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingForget = false }) { Text(stringResource(Res.string.attachment_cancel)) }
            },
        )
    }
}

internal suspend fun desktopPreview(path: Path): ImageBitmap? = withContext(Dispatchers.IO) {
    try {
        val stream = ImageIO.createImageInputStream(path.toFile()) ?: return@withContext null
        stream.use {
            val reader = ImageIO.getImageReaders(stream).asSequence().firstOrNull() ?: return@withContext null
            try {
                reader.setInput(stream, true, true)
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0 || width.toLong() * height > 40_000_000) return@withContext null
                val sample = ((maxOf(width, height) + 511) / 512).coerceAtLeast(1)
                val options = reader.defaultReadParam.apply { setSourceSubsampling(sample, sample, 0, 0) }
                reader.read(0, options)?.toComposeImageBitmap()
            } finally {
                reader.dispose()
            }
        }
    } catch (_: IOException) {
        null
    } catch (_: RuntimeException) {
        null
    }
}
