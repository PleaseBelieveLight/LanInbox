package com.luofelix.laninbox

import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.luofelix.laninbox.core.logging.AppLogger
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

    private fun resetFeedback() {
        preparation = null
        showDelivery = false
    }

    fun send() {
        val owner = pairing.activeSession.value ?: return
        if (busy()) return
        val source = file
        if (source == null) {
            preparation = if (outbox.enqueueText(owner, draft, draftKind(draft, asCode = false))) {
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
@OptIn(ExperimentalComposeUiApi::class)
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
    val enabled = !busy && !choosingFile
    val dropEnabled = rememberUpdatedState(enabled)
    val dropTarget = remember(state, logger) {
        DesktopFileDropTarget({ dropEnabled.value }, state::selectFile, logger)
    }
    val hovered = dropTarget.hovered && enabled
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
    val imageFilter = DesktopFileFilter.images(stringResource(Res.string.attachment_pick_image))
    fun chooseFile(filter: DesktopFileFilter? = null) {
        if (!enabled) return
        choosingFile = true
        dropTarget.clearError()
        scope.launch {
            try {
                chooseDesktopPath(window, FileDialog.LOAD, logger, filter = filter)?.let(state::selectFile)
            } finally {
                choosingFile = false
            }
        }
    }

    SendComposer(
        title = stringResource(if (paired) Res.string.send_to_phone else Res.string.send_prepare),
        draft = state.draft,
        onDraftChange = state::editDraft,
        file = source?.let { SelectedFile(it.fileName.toString(), size) },
        onChooseFile = { chooseFile() },
        onChooseImage = { chooseFile(imageFilter) },
        onRemove = {
            dropTarget.clearError()
            state.selectFile(null)
        },
        onSubmit = if (receiverReady && paired) state::send else onConnect,
        connectRequired = !receiverReady || !paired,
        modifier = Modifier.dragAndDropTarget(
            shouldStartDragAndDrop = dropTarget::accepts,
            target = dropTarget,
        ),
        enabled = enabled,
        failed = preparation == TransferStatus.Failed,
        highlighted = hovered,
    ) {
        Text(
            stringResource(
                when {
                    hovered -> Res.string.attachment_drop_here
                    dropTarget.error == FileDropError.SingleFileRequired -> Res.string.attachment_drop_single
                    dropTarget.error == FileDropError.UnreadableFile -> Res.string.attachment_drop_unreadable
                    else -> Res.string.attachment_drop_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (!hovered && dropTarget.error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
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
