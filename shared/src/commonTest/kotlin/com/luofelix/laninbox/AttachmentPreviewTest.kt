package com.luofelix.laninbox

import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.inbox.canPreviewImage
import com.luofelix.laninbox.feature.inbox.viewerMediaType
import com.luofelix.laninbox.feature.inbox.localPreviewKind
import com.luofelix.laninbox.feature.inbox.LocalAttachmentPreviewKind
import com.luofelix.laninbox.feature.inbox.mediaTimeLabel
import com.luofelix.laninbox.feature.inbox.AttachmentFileKind
import com.luofelix.laninbox.feature.inbox.fileKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttachmentPreviewTest {
    @Test
    fun routesGifToAnimatedViewersInsteadOfShowingOnlyTheFirstFrame() {
        assertFalse(attachment("animation.gif", "image/gif").canPreviewImage())
        assertFalse(attachment("animation.GIF", "image/png").canPreviewImage())
        assertFalse(attachment("animation", "IMAGE/GIF").canPreviewImage())
        assertEquals(LocalAttachmentPreviewKind.Gif, attachment("animation.GIF").localPreviewKind())
    }

    @Test
    fun recognizesTheRequiredLocalPhoneFormatsEvenWithGenericMimeMetadata() {
        assertEquals(LocalAttachmentPreviewKind.Video, attachment("视频.MP4").localPreviewKind())
        assertEquals(LocalAttachmentPreviewKind.Audio, attachment("音乐.MP3").localPreviewKind())
        assertEquals(LocalAttachmentPreviewKind.Text, attachment("笔记.TXT").localPreviewKind())
        assertEquals(LocalAttachmentPreviewKind.Unsupported, attachment("report.pdf").localPreviewKind())
    }

    @Test
    fun formatsMediaPositionsWithoutNegativeDurations() {
        assertEquals("0:00", mediaTimeLabel(-1))
        assertEquals("1:05", mediaTimeLabel(65_900))
        assertEquals("60:01", mediaTimeLabel(3_601_000))
    }

    @Test
    fun retainsImagePreviewsAndRecognizesGenericImageMetadata() {
        assertTrue(attachment("照片.PNG").canPreviewImage())
        assertTrue(attachment("photo", "image/jpeg").canPreviewImage())
        assertTrue(attachment("photo.webp", "image/webp").canPreviewImage())
    }

    @Test
    fun resolvesCommonMediaAndDocumentsForTheSystemAppChooser() {
        mapOf(
            "动画.GIF" to "image/gif", "video.MP4" to "video/mp4", "music.mp3" to "audio/mpeg",
            "笔记.TXT" to "text/plain", "report.pdf" to "application/pdf",
            "report.docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        ).forEach { (name, type) -> assertEquals(type, attachment(name).viewerMediaType()) }
    }

    @Test
    fun doesNotTreatAudioVideoTextOrUnknownFilesAsImages() {
        listOf("animation.gif", "video.mp4", "music.mp3", "notes.txt", "unknown.bin", "no-extension").forEach {
            assertFalse(attachment(it).canPreviewImage(), it)
        }
        assertEquals("application/octet-stream", attachment("unknown.bin").viewerMediaType())
        assertEquals("text/plain", attachment("image.png", "text/plain").viewerMediaType())
    }

    @Test
    fun distinguishesCommonFileTypesForListAndDialogIcons() {
        mapOf(
            "照片.GIF" to AttachmentFileKind.Image,
            "视频.MP4" to AttachmentFileKind.Video,
            "音乐.MP3" to AttachmentFileKind.Audio,
            "笔记.TXT" to AttachmentFileKind.Text,
            "config.JSON" to AttachmentFileKind.Code,
            "report.PDF" to AttachmentFileKind.Pdf,
            "report.DOCX" to AttachmentFileKind.Document,
            "table.XLSX" to AttachmentFileKind.Spreadsheet,
            "slides.PPTX" to AttachmentFileKind.Presentation,
            "archive.7Z" to AttachmentFileKind.Archive,
            "unknown.bin" to AttachmentFileKind.File,
        ).forEach { (name, kind) -> assertEquals(kind, attachment(name).fileKind(), name) }
    }

    @Test
    fun usesSpecificMimeTypesAndDoesNotMisclassifyDoubleExtensions() {
        assertEquals(AttachmentFileKind.Text, attachment("image.png", "text/plain").fileKind())
        assertEquals(AttachmentFileKind.Pdf, attachment("report", "APPLICATION/PDF").fileKind())
        assertEquals(AttachmentFileKind.Archive, attachment("backup", "application/x-zip-compressed").fileKind())
        assertEquals(AttachmentFileKind.Archive, attachment("backup.tar.gz").fileKind())
        assertEquals(AttachmentFileKind.File, attachment("report.pdf.exe").fileKind())
        assertEquals(AttachmentFileKind.File, attachment("notes").fileKind())
    }

    private fun attachment(name: String, type: String = "application/octet-stream") = AttachmentInfo(
        id = "12345678-1234-1234-1234-123456789abc", size = 0, sha256 = "a".repeat(64), name = name, mediaType = type,
    )
}
