package com.luofelix.laninbox.feature.inbox

import com.luofelix.laninbox.core.network.AttachmentInfo

private val viewerTypes = mapOf(
    "gif" to "image/gif", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
    "webp" to "image/webp", "bmp" to "image/bmp", "heic" to "image/heic", "avif" to "image/avif",
    "mp4" to "video/mp4", "m4v" to "video/mp4", "webm" to "video/webm", "mov" to "video/quicktime",
    "mp3" to "audio/mpeg", "m4a" to "audio/mp4", "wav" to "audio/wav", "ogg" to "audio/ogg",
    "txt" to "text/plain", "md" to "text/plain", "csv" to "text/csv", "json" to "application/json",
    "pdf" to "application/pdf", "rtf" to "application/rtf", "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "ppt" to "application/vnd.ms-powerpoint",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
)

/** Uses the filename as a viewer hint only when the sender supplied a generic MIME type. */
fun AttachmentInfo.viewerMediaType(): String {
    val type = mediaType.lowercase()
    return if (type == "application/octet-stream") {
        viewerTypes[name.substringAfterLast('.', "").lowercase()] ?: type
    } else type
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
