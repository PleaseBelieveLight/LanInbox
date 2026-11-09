package com.luofelix.laninbox

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.luofelix.laninbox.feature.inbox.canDecodeLegacyVideoPoster
import com.luofelix.laninbox.feature.inbox.videoPosterSize
import com.luofelix.laninbox.feature.inbox.videoPosterTimeUs

/** Extracts a bounded cover from local content without starting or seeking the playback decoder. */
internal fun loadVideoPoster(context: Context, uri: Uri): ImageBitmap? {
    require(uri.scheme == "content")
    val retriever = MediaMetadataRetriever()
    try {
        retriever.setDataSource(context, uri)
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        if (Build.VERSION.SDK_INT < 27) {
            // Older APIs allocate the full frame before scaling; reject unknown or excessive dimensions.
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
            if (!canDecodeLegacyVideoPoster(width, height)) return null
        }
        fun frameAt(timeUs: Long): Bitmap? = try {
            if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, 512, 512)
            } else retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (_: RuntimeException) {
            null
        }
        val frame = frameAt(videoPosterTimeUs(durationMs)) ?: frameAt(-1) ?: return null
        val size = videoPosterSize(frame.width, frame.height)
        val scaled = try {
            if (frame.width == size.width && frame.height == size.height) frame
            else Bitmap.createScaledBitmap(frame, size.width, size.height, true)
        } finally {
            if (frame.width != size.width || frame.height != size.height) frame.recycle()
        }
        return scaled.asImageBitmap()
    } finally {
        retriever.release()
    }
}
