package com.luofelix.laninbox.feature.inbox

import com.luofelix.laninbox.core.network.AttachmentInfo

private val viewerTypes = mapOf(
    "gif" to "image/gif", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
    "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "avif" to "image/avif",
    "svg" to "image/svg+xml", "tif" to "image/tiff", "tiff" to "image/tiff", "ico" to "image/x-icon",
    "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm", "mov" to "video/quicktime",
    "mkv" to "video/x-matroska", "avi" to "video/x-msvideo", "3gp" to "video/3gpp",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav", "ogg" to "audio/ogg",
    "flac" to "audio/flac", "aac" to "audio/aac", "opus" to "audio/opus",
    "txt" to "text/plain", "md" to "text/plain", "csv" to "text/csv", "json" to "application/json",
    "xml" to "application/xml", "log" to "text/plain", "html" to "text/html", "css" to "text/css",
    "pdf" to "application/pdf", "rtf" to "application/rtf", "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "odt" to "application/vnd.oasis.opendocument.text", "ods" to "application/vnd.oasis.opendocument.spreadsheet",
    "odp" to "application/vnd.oasis.opendocument.presentation", "zip" to "application/zip",
    "rar" to "application/vnd.rar", "7z" to "application/x-7z-compressed", "tar" to "application/x-tar",
    "gz" to "application/gzip", "bz2" to "application/x-bzip2", "xz" to "application/x-xz",
)

/** Uses the filename as a viewer hint only when the sender supplied a generic MIME type. */
fun AttachmentInfo.viewerMediaType(): String {
    val type = mediaType.lowercase()
    return if (type == "application/octet-stream") {
        viewerTypes[name.substringAfterLast('.', "").lowercase()] ?: type
    } else type
}

internal enum class AttachmentFileKind { Image, Video, Audio, Text, Code, Pdf, Document, Spreadsheet, Presentation, Archive, File }

/** Keeps list and dialog icons consistent, respecting specific sender MIME types over filename hints. */
internal fun AttachmentInfo.fileKind(): AttachmentFileKind {
    val type = viewerMediaType()
    return when {
        type.startsWith("image/") -> AttachmentFileKind.Image
        type.startsWith("video/") -> AttachmentFileKind.Video
        type.startsWith("audio/") -> AttachmentFileKind.Audio
        type == "application/pdf" -> AttachmentFileKind.Pdf
        type in setOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.oasis.opendocument.spreadsheet", "text/csv") -> AttachmentFileKind.Spreadsheet
        type in setOf("application/vnd.ms-powerpoint", "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.oasis.opendocument.presentation") -> AttachmentFileKind.Presentation
        type in setOf("application/msword", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.oasis.opendocument.text", "application/rtf", "text/rtf") -> AttachmentFileKind.Document
        type in setOf("application/zip", "application/x-zip-compressed", "application/vnd.rar", "application/x-rar-compressed",
            "application/x-7z-compressed", "application/x-tar", "application/gzip", "application/x-gzip",
            "application/x-bzip2", "application/x-xz") -> AttachmentFileKind.Archive
        type in setOf("application/json", "application/xml", "text/xml", "text/html", "text/css", "application/javascript", "text/javascript") -> AttachmentFileKind.Code
        type.startsWith("text/") -> AttachmentFileKind.Text
        else -> AttachmentFileKind.File
    }
}

internal fun AttachmentInfo.canPreviewImage(): Boolean {
    val type = viewerMediaType()
    // ImageBitmap previews cannot play animations; offer a real viewer for GIFs instead of a still frame.
    return type.startsWith("image/") && type != "image/gif" && !name.endsWith(".gif", ignoreCase = true)
}

enum class LocalAttachmentPreviewKind { Gif, Video, Audio, Text, Unsupported }

/** Identifies formats handled by the Android local viewer without fetching remote content. */
fun AttachmentInfo.localPreviewKind(): LocalAttachmentPreviewKind = when {
    name.endsWith(".gif", ignoreCase = true) || viewerMediaType() == "image/gif" -> LocalAttachmentPreviewKind.Gif
    viewerMediaType().startsWith("video/") -> LocalAttachmentPreviewKind.Video
    viewerMediaType().startsWith("audio/") -> LocalAttachmentPreviewKind.Audio
    viewerMediaType().startsWith("text/") || viewerMediaType() in setOf("application/json", "application/xml") -> LocalAttachmentPreviewKind.Text
    else -> LocalAttachmentPreviewKind.Unsupported
}

data class AttachmentTextPreview(val text: String, val truncated: Boolean)

internal fun mediaTimeLabel(positionMs: Int): String {
    val seconds = positionMs.coerceAtLeast(0) / 1_000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
