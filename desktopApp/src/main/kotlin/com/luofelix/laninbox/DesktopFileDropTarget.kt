package com.luofelix.laninbox

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.draganddrop.awtTransferable
import com.luofelix.laninbox.core.logging.AppLogger
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

internal enum class FileDropError { SingleFileRequired, UnreadableFile }

/** Attaches one readable file to the draft without sending it or changing the source file. */
@OptIn(ExperimentalComposeUiApi::class)
internal class DesktopFileDropTarget(
    private val isEnabled: () -> Boolean,
    private val onFile: (Path) -> Unit,
    private val logger: AppLogger,
) : DragAndDropTarget {
    var hovered by mutableStateOf(false)
        private set
    var error by mutableStateOf<FileDropError?>(null)
        private set

    fun accepts(transferable: Transferable, action: DragAndDropTransferAction?): Boolean =
        isEnabled() && action == DragAndDropTransferAction.Copy &&
            transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)

    fun accepts(event: DragAndDropEvent): Boolean =
        supportsCopy(event) && accepts(event.awtTransferable, DragAndDropTransferAction.Copy)

    private fun supportsCopy(event: DragAndDropEvent): Boolean {
        val sourceActions = when (val native = event.nativeEvent) {
            is DropTargetDragEvent -> native.sourceActions
            is DropTargetDropEvent -> native.sourceActions
            else -> return false
        }
        // Explorer can request Move by default even when the source also supports Copy.
        return sourceActions and DnDConstants.ACTION_COPY != 0
    }

    /** Returns false for rejected drops and leaves the current draft attachment intact. */
    fun drop(transferable: Transferable, action: DragAndDropTransferAction?): Boolean {
        hovered = false
        // Only read a payload after the caller has negotiated a non-destructive Copy.
        if (!accepts(transferable, action)) return false
        val file = try {
            // Read the native payload before returning: its lifetime ends with the drop callback.
            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>
            if (files?.size != 1) {
                error = FileDropError.SingleFileRequired
                return false
            }
            val path = (files.single() as? File)?.toPath()
            if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
                error = FileDropError.UnreadableFile
                return false
            }
            path
        } catch (error: Exception) {
            logger.warn("File drop failed", "reason" to error.javaClass.simpleName)
            this.error = FileDropError.UnreadableFile
            return false
        }
        clearError()
        onFile(file)
        return true
    }

    fun clearError() {
        error = null
    }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        hovered = false
        val native = event.nativeEvent as? DropTargetDropEvent ?: return false
        if (!accepts(event)) return false
        // Compose initially accepts the requested action. Override it before reading the payload
        // so dropComplete cannot tell Explorer to delete the source after attaching it.
        native.acceptDrop(DnDConstants.ACTION_COPY)
        return drop(native.transferable, DragAndDropTransferAction.Copy)
    }

    override fun onEntered(event: DragAndDropEvent) {
        hovered = accepts(event)
    }

    override fun onMoved(event: DragAndDropEvent) = onEntered(event)

    override fun onChanged(event: DragAndDropEvent) = onEntered(event)

    override fun onExited(event: DragAndDropEvent) {
        hovered = false
    }

    override fun onEnded(event: DragAndDropEvent) {
        hovered = false
    }
}
