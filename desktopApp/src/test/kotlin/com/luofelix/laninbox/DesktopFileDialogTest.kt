package com.luofelix.laninbox

import com.sun.jna.CallbackReference
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.COM.COMException
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinNT.HRESULT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary.StdCallCallback
import java.awt.FileDialog
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue

class DesktopFileDialogTest {
    @Test
    fun imageFilterIncludesCommonImageTypesAndRejectsDocuments() {
        val filter = DesktopFileFilter.images("图片")
        listOf("照片.PNG", "photo.jpeg", "animation.gif", "picture.webp", "photo.heic", "vector.svg").forEach {
            assertTrue(filter.accepts(it), it)
        }
        listOf("notes.txt", "report.pdf", "photo.jpg.exe", "image").forEach {
            assertFalse(filter.accepts(it), it)
        }
    }

    @Test
    fun modernImageDialogAcceptsLocalizedImageFilter() {
        assumeTrue(Platform.isWindows())
        withWindowsFileDialog(FileDialog.LOAD, filter = DesktopFileFilter.images("图片")) { dialog ->
            val index = IntByReference()
            COMUtils.checkRC(DialogInspection(dialog.pointer).getFileTypeIndex(index))
            assertEquals(1, index.value)
            assertTrue(dialog.options and 0x1000 != 0) // FOS_FILEMUSTEXIST
        }
    }

    @Test
    fun imageDialogStartsInTheCurrentWindowsPicturesFolder() {
        assumeTrue(Platform.isWindows())
        withWindowsFileDialog(
            FileDialog.LOAD, filter = DesktopFileFilter.images("图片"), startInPictures = true,
        ) { dialog ->
            val pictures = Path.of(Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_Pictures))
            assertEquals(pictures.toRealPath(), dialog.folderPath().toRealPath())
            val index = IntByReference()
            COMUtils.checkRC(DialogInspection(dialog.pointer).getFileTypeIndex(index))
            assertEquals(1, index.value)
        }
    }

    @Test
    fun explicitFolderOverridesThePreviousLocationAndPreservesUnicodePaths() {
        assumeTrue(Platform.isWindows())
        val root = Files.createTempDirectory("laninbox-dialog-folder-")
        val first = Files.createDirectory(root.resolve("先前目录"))
        val pictures = Files.createDirectory(root.resolve("图片 中文 空格"))
        try {
            withWindowsFileDialog(FileDialog.LOAD) { dialog ->
                dialog.setFolder(first)
                assertEquals(first.toRealPath(), dialog.folderPath().toRealPath())
                dialog.setFolder(pictures)
                assertEquals(pictures.toRealPath(), dialog.folderPath().toRealPath())
            }
        } finally {
            Files.deleteIfExists(pictures)
            Files.deleteIfExists(first)
            Files.deleteIfExists(root)
        }
    }

    @Test
    fun modernOpenDialogRequiresOneExistingFileSystemPath() {
        assumeTrue(Platform.isWindows())
        withWindowsFileDialog(FileDialog.LOAD) { dialog ->
            assertTrue(dialog.options and 0x40 != 0) // FOS_FORCEFILESYSTEM
            assertTrue(dialog.options and 0x800 != 0) // FOS_PATHMUSTEXIST
            assertTrue(dialog.options and 0x1000 != 0) // FOS_FILEMUSTEXIST
            assertFalse(dialog.options and 0x200 != 0) // FOS_ALLOWMULTISELECT
        }
    }

    @Test
    fun modernSaveDialogKeepsUnicodeSuggestedNameAndOverwritePrompt() {
        assumeTrue(Platform.isWindows())
        val name = "传输附件 中文 空格.txt"
        withWindowsFileDialog(FileDialog.SAVE, name) { dialog ->
            assertTrue(dialog.options and 0x40 != 0) // FOS_FORCEFILESYSTEM
            assertTrue(dialog.options and 0x2 != 0) // FOS_OVERWRITEPROMPT
            assertTrue(dialog.options and 0x800 != 0) // FOS_PATHMUSTEXIST
            assertFalse(dialog.options and 0x1000 != 0) // FOS_FILEMUSTEXIST
            val result = PointerByReference()
            COMUtils.checkRC(DialogInspection(dialog.pointer).getFileName(result))
            try {
                assertEquals(name, result.value.getWideString(0))
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(result.value)
            }
        }
    }

    @Test
    fun cancellationReturnsNoSelectionInsteadOfAnError() {
        assumeTrue(Platform.isWindows())
        withShowResult(0x800704C7.toInt()) { dialog, owner ->
            assertFalse(dialog.show(owner))
        }
    }

    @Test
    fun unexpectedNativeFailuresAreReported() {
        assumeTrue(Platform.isWindows())
        withShowResult(0x80004005.toInt()) { dialog, owner ->
            assertFailsWith<COMException> { dialog.show(owner) }
        }
    }

    private fun withShowResult(result: Int, action: (WindowsFileDialog, HWND) -> Unit) {
        val owner = HWND(Pointer.createConstant(1234))
        val show = object : ShowCallback {
            override fun invoke(instance: Pointer, window: Pointer): Int {
                assertEquals(owner.pointer, window)
                return result
            }
        }
        Memory(Native.POINTER_SIZE.toLong() * 4).use { table ->
            Memory(Native.POINTER_SIZE.toLong()).use { instance ->
                table.setPointer(Native.POINTER_SIZE.toLong() * 3, CallbackReference.getFunctionPointer(show))
                instance.setPointer(0, table)
                action(WindowsFileDialog(instance), owner)
            }
        }
    }

    private interface ShowCallback : StdCallCallback {
        fun invoke(instance: Pointer, window: Pointer): Int
    }

    private class DialogInspection(pointer: Pointer) : Unknown(pointer) {
        fun getFileTypeIndex(result: IntByReference): HRESULT =
            _invokeNativeObject(6, arrayOf(pointer, result), HRESULT::class.java) as HRESULT

        fun getFileName(result: PointerByReference): HRESULT =
            _invokeNativeObject(16, arrayOf(pointer, result), HRESULT::class.java) as HRESULT
    }
}
