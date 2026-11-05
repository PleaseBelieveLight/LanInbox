package com.luofelix.laninbox

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTransferAction
import androidx.compose.ui.geometry.Offset
import com.luofelix.laninbox.core.logging.AppLogger
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalComposeUiApi::class)
class DesktopFileDropTargetTest {
    @Test
    fun attachesUnicodeFileWithoutChangingItsContents() = withDirectory { directory ->
        val path = Files.writeString(directory.resolve("附件 中文 空格.txt"), "文件内容")
        val selected = mutableListOf<Path>()
        val target = target(onFile = selected::add)
        val payload = files(path)

        assertTrue(target.accepts(payload, DragAndDropTransferAction.Copy))
        assertTrue(target.drop(payload, DragAndDropTransferAction.Copy))
        assertEquals(listOf(path), selected)
        assertEquals("文件内容", Files.readString(path))
        assertNull(target.error)
    }

    @Test
    fun rejectedDropsKeepThePreviouslySelectedFile() = withDirectory { directory ->
        val first = Files.createFile(directory.resolve("first.txt"))
        val second = Files.createFile(directory.resolve("second.txt"))
        val selected = mutableListOf<Path>()
        val target = target(onFile = selected::add)
        assertTrue(target.drop(files(first), DragAndDropTransferAction.Copy))

        assertFalse(target.drop(files(first, second), DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.SingleFileRequired, target.error)
        assertFalse(target.drop(files(), DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.SingleFileRequired, target.error)
        assertFalse(target.drop(files(directory), DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.UnreadableFile, target.error)
        assertFalse(target.drop(files(directory.resolve("missing.txt")), DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.UnreadableFile, target.error)
        assertEquals(listOf(first), selected)

        assertTrue(target.drop(files(second), DragAndDropTransferAction.Copy))
        assertEquals(listOf(first, second), selected)
        assertNull(target.error)
    }

    @Test
    fun textAndDestructiveTransferActionsAreIgnored() = withDirectory { directory ->
        val path = Files.createFile(directory.resolve("source.txt"))
        val selected = mutableListOf<Path>()
        val target = target(onFile = selected::add)
        val text = StringSelection(path.toString())
        assertFalse(target.accepts(text, DragAndDropTransferAction.Copy))
        assertFalse(target.drop(text, DragAndDropTransferAction.Copy))
        for (action in listOf(DragAndDropTransferAction.Move, DragAndDropTransferAction.Link, null)) {
            assertFalse(target.accepts(files(path), action))
            assertFalse(target.drop(files(path), action))
        }
        assertTrue(selected.isEmpty())
        assertTrue(Files.exists(path))
    }

    @Test
    fun disabledComposerDoesNotReadOrReplaceTheFile() {
        var enabled = true
        val target = target(isEnabled = { enabled }, onFile = { error("Must not select a file") })
        val payload = payload { error("Must not read the payload") }
        assertTrue(target.accepts(payload, DragAndDropTransferAction.Copy))
        // The composer can become busy after the native drag has already started.
        enabled = false
        assertFalse(target.accepts(payload, DragAndDropTransferAction.Copy))
        assertFalse(target.drop(payload, DragAndDropTransferAction.Copy))
        assertNull(target.error)
    }

    @Test
    fun nativeReadFailuresShowFeedbackWithoutLoggingFilePaths() {
        val logs = mutableListOf<String>()
        val target = DesktopFileDropTarget(
            isEnabled = { true },
            onFile = { error("Must not select a file") },
            logger = AppLogger { logs += it.render() },
        )
        assertFalse(target.drop(payload { throw IOException("private/path.txt") }, DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.UnreadableFile, target.error)
        assertEquals(listOf("File drop failed reason=IOException"), logs)
        target.clearError()
        assertNull(target.error)
    }

    @Test
    fun malformedNativePayloadsAreRejected() {
        val target = target(onFile = { error("Must not select a file") })
        assertFalse(target.drop(payload { "not a file list" }, DragAndDropTransferAction.Copy))
        assertFalse(target.drop(payload { listOf("not a file") }, DragAndDropTransferAction.Copy))
        assertEquals(FileDropError.UnreadableFile, target.error)
    }

    @Test
    fun highlightClearsOnExitEndAndActionChange() {
        val target = target()
        val copy = DragAndDropEvent(DragAndDropTransferAction.Copy, null, Offset.Zero)
        val move = DragAndDropEvent(DragAndDropTransferAction.Move, null, Offset.Zero)
        target.onEntered(copy)
        assertTrue(target.hovered)
        target.onExited(copy)
        assertFalse(target.hovered)
        target.onEntered(copy)
        target.onEnded(copy)
        assertFalse(target.hovered)
        target.onEntered(copy)
        target.onChanged(move)
        assertFalse(target.hovered)
        target.onChanged(copy)
        assertTrue(target.hovered)
    }

    private fun target(isEnabled: () -> Boolean = { true }, onFile: (Path) -> Unit = {}) =
        DesktopFileDropTarget(isEnabled, onFile, AppLogger {})

    private fun files(vararg paths: Path): Transferable = payload { paths.map(Path::toFile) }

    private fun payload(read: () -> Any): Transferable = object : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(DataFlavor.javaFileListFlavor)

        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == DataFlavor.javaFileListFlavor

        override fun getTransferData(flavor: DataFlavor): Any = read()
    }

    private fun withDirectory(test: (Path) -> Unit) {
        val directory = Files.createTempDirectory("laninbox-file-drop-test")
        try {
            test(directory)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
