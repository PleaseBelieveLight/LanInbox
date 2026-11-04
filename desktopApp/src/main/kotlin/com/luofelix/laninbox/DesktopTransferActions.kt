package com.luofelix.laninbox

import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.luofelix.laninbox.feature.inbox.PairingPanel
import com.luofelix.laninbox.feature.send.DesktopTextSend
import com.luofelix.laninbox.feature.send.TransferControls
import com.luofelix.laninbox.feature.send.TransferStatus
import java.awt.FileDialog
import java.io.IOException
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun DesktopTransferActions(
    window: ComposeWindow,
    host: String,
    fingerprint: String,
    pairing: DesktopPairingSession,
    outbox: DesktopOutbox,
) {
    val scope = rememberCoroutineScope()
    val session by pairing.activeSession.collectAsState()
    val invitation by pairing.invitation.collectAsState()
    val queued by outbox.delivery.collectAsState()
    val delivered by outbox.delivered.collectAsState()
    var staging by remember { mutableStateOf<Job?>(null) }
    var lastSource by remember { mutableStateOf<Path?>(null) }
    var preparation by remember { mutableStateOf<TransferStatus?>(null) }
    LaunchedEffect(session, queued?.owner) {
        if (queued?.owner != null && queued?.owner != session) outbox.cancel()
    }
    DisposableEffect(outbox) { onDispose { outbox.cancel() } }

    fun prepare(path: Path) {
        val owner = session ?: return
        if (staging?.isActive == true || queued != null) return
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

    SelectionContainer { Text("$host:8443") }
    SelectionContainer { Text(fingerprint) }
    PairingPanel(invitation, pairing::issueInvitation) {
        staging?.cancel()
        outbox.cancel()
        pairing.revoke()
    }
    val active = queued
    val attachment = active?.delivery?.attachment
    val idle = active == null && preparation != TransferStatus.Preparing
    val ready = session != null && idle
    DesktopTextSend(ready) { text, kind ->
        val owner = session ?: return@DesktopTextSend false
        preparation = null
        outbox.enqueueText(owner, text, kind)
    }
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
        choose = if (ready) ({ chooseDesktopPath(window, FileDialog.LOAD)?.let(::prepare) }) else null,
        cancel = if (!idle) ({
            staging?.cancel()
            outbox.cancel()
            preparation = TransferStatus.Canceled
        }) else null,
        retry = if (ready && (preparation == TransferStatus.Canceled || preparation == TransferStatus.Failed))
            ({ lastSource?.let(::prepare) }) else null,
    )
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
