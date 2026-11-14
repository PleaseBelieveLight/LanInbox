package com.luofelix.laninbox

import androidx.compose.ui.awt.ComposeWindow
import com.luofelix.laninbox.core.logging.AppLogger
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid.GUID
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.WTypes.CLSCTX_INPROC_SERVER
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.awt.FileDialog
import java.io.File
import java.nio.file.Path
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

internal class DesktopFileFilter private constructor(val label: String, val extensions: List<String>) {
    fun accepts(name: String): Boolean = extensions.any { name.endsWith(".$it", ignoreCase = true) }

    companion object {
        fun images(label: String) = DesktopFileFilter(
            label,
            listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "tif", "tiff", "heic", "heif", "avif", "svg", "ico"),
        )
    }
}

/**
 * Returns a selected path, or null on cancellation, without blocking Compose's event thread.
 * [startInPictures] opens the current Windows Pictures folder instead of the remembered location.
 */
internal suspend fun chooseDesktopPath(
    window: ComposeWindow,
    mode: Int,
    logger: AppLogger,
    name: String? = null,
    filter: DesktopFileFilter? = null,
    startInPictures: Boolean = false,
): Path? = withContext(Dispatchers.Swing) {
    require(mode == FileDialog.LOAD || mode == FileDialog.SAVE)
    var initialDirectory: Path? = null
    if (Platform.isWindows()) {
        try {
            val owner = HWND(Native.getWindowPointer(window))
            // A dedicated thread avoids inheriting another caller's incompatible COM apartment.
            Executors.newSingleThreadExecutor { task ->
                Thread(task, "LanInbox-file-dialog").apply { isDaemon = true }
            }.asCoroutineDispatcher().use { dispatcher ->
                return@withContext withContext(dispatcher) {
                    withWindowsFileDialog(mode, name, filter, startInPictures) { dialog ->
                        if (startInPictures) initialDirectory = dialog.folderPath()
                        if (dialog.show(owner)) dialog.selectedPath() else null
                    }
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: RuntimeException) {
            logger.warn("Modern file dialog failed", "reason" to error.javaClass.simpleName)
        } catch (error: LinkageError) {
            logger.warn("Modern file dialog unavailable", "reason" to error.javaClass.simpleName)
        }
    }
    // Other platforms and native initialization failures retain the existing system picker.
    val picker = FileDialog(window, null, mode)
    try {
        picker.file = name
        if (startInPictures) {
            val pictures = initialDirectory ?: Path.of(System.getProperty("user.home"), "Pictures")
                .takeIf { it.toFile().isDirectory }
            picker.directory = pictures?.toString()
        }
        if (filter != null) {
            picker.setFilenameFilter { directory, file ->
                File(directory, file).isDirectory || filter.accepts(file)
            }
        }
        picker.isVisible = true
        val file = picker.file ?: return@withContext null
        val directory = picker.directory ?: return@withContext null
        Path.of(directory, file)
    } finally {
        picker.dispose()
    }
}

/** Keeps creation, use, release, and COM teardown on the caller's STA thread. */
internal fun <T> withWindowsFileDialog(
    mode: Int,
    name: String? = null,
    filter: DesktopFileFilter? = null,
    startInPictures: Boolean = false,
    action: (WindowsFileDialog) -> T,
): T {
    require(mode == FileDialog.LOAD || mode == FileDialog.SAVE)
    val ole = Ole32.INSTANCE
    COMUtils.checkRC(ole.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED or Ole32.COINIT_DISABLE_OLE1DDE))
    try {
        val result = PointerByReference()
        val classId = if (mode == FileDialog.SAVE) {
            "{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}" // CLSID_FileSaveDialog
        } else {
            "{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}" // CLSID_FileOpenDialog
        }
        COMUtils.checkRC(
            ole.CoCreateInstance(
                GUID(classId), null, CLSCTX_INPROC_SERVER,
                GUID("{42F85136-DB7E-439C-85F1-E4075D135FC8}"), // IID_IFileDialog
                result,
            ),
        )
        val dialog = WindowsFileDialog(result.value)
        try {
            // Preserve Windows defaults, including overwrite confirmation and existing-file checks.
            dialog.options = dialog.options or 0x40 // FOS_FORCEFILESYSTEM: callers need a real Path.
            if (name != null) dialog.setFileName(name)
            if (filter != null) dialog.setFileFilter(filter)
            if (startInPictures) {
                // Query the current known-folder path so relocated and OneDrive folders are respected.
                dialog.setFolder(Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Pictures)))
            }
            return action(dialog)
        } finally {
            dialog.Release()
        }
    } finally {
        // S_FALSE also increments COM's initialization count and must be balanced.
        ole.CoUninitialize()
    }
}

/** Minimal IFileDialog binding; method slots follow the Windows SDK's shobjidl_core.h. */
internal class WindowsFileDialog(pointer: Pointer) : Unknown(pointer) {
    private var filterSpec: WindowsFilterSpec? = null

    var options: Int
        get() {
            val result = IntByReference()
            COMUtils.checkRC(invoke(10, result)) // IFileDialog::GetOptions
            return result.value
        }
        set(value) {
            COMUtils.checkRC(invoke(9, value)) // IFileDialog::SetOptions
        }

    fun setFileName(name: String) {
        COMUtils.checkRC(invoke(15, WString(name))) // IFileDialog::SetFileName
    }

    /** Overrides Windows' remembered location for this dialog without changing system folder settings. */
    fun setFolder(directory: Path) {
        val result = PointerByReference()
        COMUtils.checkRC(
            FileDialogShell.instance.SHCreateItemFromParsingName(
                WString(directory.toAbsolutePath().toString()), null,
                GUID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}"), // IID_IShellItem
                result,
            ),
        )
        val item = WindowsShellItem(result.value)
        try {
            // SetDefaultFolder yields to the last-used location; the Pictures action must always start here.
            COMUtils.checkRC(invoke(12, item.pointer)) // IFileDialog::SetFolder
        } finally {
            item.Release()
        }
    }

    fun setFileFilter(filter: DesktopFileFilter) {
        // Keep the UTF-16 strings and their structure alive until the dialog is released.
        val spec = WindowsFilterSpec().apply {
            name = WString(filter.label)
            patterns = WString(filter.extensions.joinToString(";") { "*.$it" })
        }
        filterSpec = spec
        COMUtils.checkRC(invoke(4, 1, spec)) // IFileDialog::SetFileTypes
        COMUtils.checkRC(invoke(5, 1)) // IFileDialog::SetFileTypeIndex uses one-based indexes.
    }

    fun show(owner: HWND): Boolean {
        val result = invoke(3, owner) // IModalWindow::Show
        if (result.toInt() == 0x800704C7.toInt()) return false // HRESULT_FROM_WIN32(ERROR_CANCELLED)
        COMUtils.checkRC(result)
        return true
    }

    /** Returns the current folder, including the initial folder before the dialog is shown. */
    fun folderPath(): Path = shellItemPath(13) // IFileDialog::GetFolder

    fun selectedPath(): Path = shellItemPath(20) // IFileDialog::GetResult

    private fun shellItemPath(slot: Int): Path {
        val result = PointerByReference()
        COMUtils.checkRC(invoke(slot, result))
        val item = WindowsShellItem(result.value)
        try {
            return item.path()
        } finally {
            item.Release()
        }
    }

    private fun invoke(slot: Int, vararg arguments: Any): HRESULT =
        _invokeNativeObject(slot, arrayOf(pointer, *arguments), HRESULT::class.java) as HRESULT
}

private interface FileDialogShell : StdCallLibrary {
    fun SHCreateItemFromParsingName(
        path: WString,
        bindingContext: Pointer?,
        interfaceId: GUID,
        result: PointerByReference,
    ): HRESULT

    companion object {
        val instance: FileDialogShell by lazy { Native.load("shell32", FileDialogShell::class.java) }
    }
}

@Structure.FieldOrder("name", "patterns")
internal class WindowsFilterSpec : Structure() {
    @JvmField var name: WString? = null
    @JvmField var patterns: WString? = null
}

private class WindowsShellItem(pointer: Pointer) : Unknown(pointer) {
    fun path(): Path {
        val result = PointerByReference()
        COMUtils.checkRC(
            _invokeNativeObject(
                5, arrayOf(pointer, 0x80058000.toInt(), result), HRESULT::class.java,
            ) as HRESULT, // IShellItem::GetDisplayName(SIGDN_FILESYSPATH)
        )
        try {
            // Shell strings are UTF-16 allocations, with no fixed MAX_PATH-sized buffer.
            return Path.of(result.value.getWideString(0))
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(result.value)
        }
    }
}
