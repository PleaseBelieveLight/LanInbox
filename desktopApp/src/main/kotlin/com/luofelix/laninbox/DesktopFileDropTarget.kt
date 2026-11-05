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

    /** Returns false for rejected drops and leaves the current draft attachment intact. */
    fun drop(transferable: Transferable, action: DragAndDropTransferAction?): Boolean {
        hovered = false
        // Compose completes the native drop with the requested action. Reject Move so the source
        // application cannot delete a file that we only attached for a later send.
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

    override fun onDrop(event: DragAndDropEvent): Boolean = drop(event.awtTransferable, event.action)

    override fun onEntered(event: DragAndDropEvent) {
        hovered = isEnabled() && event.action == DragAndDropTransferAction.Copy
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
