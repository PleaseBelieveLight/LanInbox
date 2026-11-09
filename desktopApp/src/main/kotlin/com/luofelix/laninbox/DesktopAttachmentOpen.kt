package com.luofelix.laninbox

import androidx.compose.ui.awt.ComposeWindow
import com.luofelix.laninbox.core.logging.AppLogger
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.win32.StdCallLibrary
import java.awt.Desktop
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

/** Opens a chooser on Windows and reports launch failures without blocking Compose's event thread. */
internal suspend fun openDesktopAttachment(
    window: ComposeWindow,
    source: Path,
    attachment: AttachmentInfo,
    logger: AppLogger,
): Boolean = withContext(Dispatchers.Swing) {
    try {
        val file = withContext(Dispatchers.IO) { prepareDesktopAttachmentForOpen(source, attachment) }
        if (Platform.isWindows()) {
            val owner = HWND(Native.getWindowPointer(window))
            // Shell UI requires a dedicated STA, as does the native file picker.
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "LanInbox-open-with").apply { isDaemon = true }
            }.asCoroutineDispatcher().use { dispatcher ->
                withContext(dispatcher) {
                    val ole = Ole32.INSTANCE
                    COMUtils.checkRC(ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE))
                    try {
                        val result = OpenWithShell.instance.SHOpenWithDialog(owner, WindowsOpenAsInfo(file))
                        // Closing the chooser is a normal outcome, not a viewer failure.
                        if (result.toInt() != 0x800704C7.toInt()) COMUtils.checkRC(result)
                    } finally {
                        ole.CoUninitialize()
                    }
                }
            }
        } else {
            withContext(Dispatchers.IO) { Desktop.getDesktop().open(file.toFile()) }
        }
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logger.warn("Attachment viewer failed", "attachmentId" to attachment.id, "type" to error.javaClass.simpleName)
        false
    } catch (error: LinkageError) {
        logger.warn("Attachment viewer unavailable", "reason" to error.javaClass.simpleName)
        false
    }
}

/** Preserves the name and extension for shell matching without letting external editors change the inbox. */
internal fun prepareDesktopAttachmentForOpen(source: Path, attachment: AttachmentInfo): Path {
    require(attachment.isValid())
    val directory = Files.createTempDirectory("LanInbox-open-")
    val destination = directory.resolve(attachment.name)
    directory.toFile().deleteOnExit()
    try {
        Files.copy(source, destination)
        destination.toFile().deleteOnExit()
        return destination
    } catch (error: Exception) {
        Files.deleteIfExists(destination)
        Files.deleteIfExists(directory)
        throw error
    }
}

@Structure.FieldOrder("file", "fileClass", "flags")
internal class WindowsOpenAsInfo(path: Path) : Structure() {
    @JvmField var file: WString = WString(path.toAbsolutePath().toString())
    @JvmField var fileClass: WString? = null
    @JvmField var flags: Int = 0x4 or 0x20 // OAIF_EXEC | OAIF_HIDE_REGISTRATION; never change file associations.
}

private interface OpenWithShell : StdCallLibrary {
    fun SHOpenWithDialog(owner: HWND, info: WindowsOpenAsInfo): HRESULT

    companion object {
        val instance: OpenWithShell by lazy { Native.load("shell32", OpenWithShell::class.java) }
    }
}
