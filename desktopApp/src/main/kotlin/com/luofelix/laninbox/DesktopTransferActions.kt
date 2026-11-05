package com.luofelix.laninbox

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.luofelix.laninbox.feature.inbox.PairingPanel
import com.luofelix.laninbox.core.network.PairingInvitation
import com.luofelix.laninbox.core.network.privateLanAuthority
import com.luofelix.laninbox.core.network.SECURE_PORT
import com.luofelix.laninbox.feature.send.DesktopTextSend
import com.luofelix.laninbox.feature.send.DeviceNameEditor
import com.luofelix.laninbox.feature.send.TransferControls
import com.luofelix.laninbox.feature.send.TransferStatus
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The window owns pending preparation even when the inbox header scrolls out of composition. */
internal class DesktopTransferState(
    private val scope: CoroutineScope,
    private val pairing: DesktopPairingSession,
    private val outbox: DesktopOutbox,
) {
    private var staging: Job? = null
    var lastSource by mutableStateOf<Path?>(null)
        private set
    var preparation by mutableStateOf<TransferStatus?>(null)
        private set

    fun prepare(path: Path) {
        val owner = pairing.activeSession.value ?: return
        if (staging?.isActive == true || outbox.delivery.value != null) return
        lastSource = path
        preparation = TransferStatus.Preparing
        staging = scope.launch {
            try {
                preparation = if (outbox.enqueueFile(owner, path)) null else TransferStatus.Failed
            } catch (error: CancellationException) {
                preparation = TransferStatus.Canceled
                throw error
            } catch (_: IOException) {
                preparation = TransferStatus.Failed
            }
        }
    }

    fun cancel() {
        staging?.cancel()
        outbox.cancel()
        preparation = TransferStatus.Canceled
    }

    fun revoke() {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { pairing.revoke() }
                cancel()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                preparation = TransferStatus.Failed
            }
        }
    }

    fun sendText(text: String, kind: com.luofelix.laninbox.core.network.MessageKind): Boolean {
        val owner = pairing.activeSession.value ?: return false
        preparation = null
        return outbox.enqueueText(owner, text, kind)
    }
}

@Composable
internal fun DesktopTransferActions(
    window: ComposeWindow,
    receiverReady: Boolean,
    host: String?,
    fingerprint: String,
    pairing: DesktopPairingSession,
    outbox: DesktopOutbox,
    state: DesktopTransferState,
    deviceName: String,
    onDeviceName: (String) -> Unit,
) {
    val session by pairing.activeSession.collectAsState()
    val queued by outbox.delivery.collectAsState()
    val delivered by outbox.delivered.collectAsState()
    val preparation = state.preparation
    val lastSource = state.lastSource
    val active = queued
    val attachment = active?.delivery?.attachment
    val idle = active == null && preparation != TransferStatus.Preparing
    val ready = receiverReady && session != null && idle
    val status = when {
        active != null -> TransferStatus.Sending
        preparation != null -> preparation
        delivered -> TransferStatus.Delivered
        else -> null
    }
    TransferControls(
        status = status,
        progress = attachment?.let {
            if (it.size == 0L) 1f else (active.transferred.toDouble() / it.size).toFloat().coerceIn(0f, 1f)
        },
        name = attachment?.name ?: lastSource?.fileName?.toString()?.takeIf { preparation != null },
        choose = { chooseDesktopPath(window, FileDialog.LOAD)?.let(state::prepare) },
        chooseEnabled = receiverReady && idle,
        pairingRequired = receiverReady && session == null,
        cancel = if (!idle) state::cancel else null,
        retry = if (ready && (preparation == TransferStatus.Canceled || preparation == TransferStatus.Failed))
            ({ lastSource?.let(state::prepare) }) else null,
    )
    if (!receiverReady) return

    val invitation by pairing.invitation.collectAsState()
    val address = host?.let { "$it:$SECURE_PORT" }
    DeviceNameEditor(deviceName, onDeviceName)
    address?.let { SelectionContainer { Text(it) } }
    SelectionContainer { Text(fingerprint) }
    val code = invitation
    val qr = remember(code, address, fingerprint) {
        if (code != null && address != null && privateLanAuthority(address, SECURE_PORT) != null) {
            pairingQr(PairingInvitation(address, fingerprint, code))
        } else null
    }
    PairingPanel(invitation, qr, pairing::issueInvitation, state::revoke)
    DesktopTextSend(ready, state::sendText)
}

internal fun chooseDesktopPath(window: ComposeWindow, mode: Int, name: String? = null): Path? {
    val picker = FileDialog(window, null, mode)
    try {
        picker.file = name
        picker.isVisible = true
        val file = picker.file ?: return null
        val directory = picker.directory ?: return null
        return Path.of(directory, file)
    } finally {
        picker.dispose()
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
