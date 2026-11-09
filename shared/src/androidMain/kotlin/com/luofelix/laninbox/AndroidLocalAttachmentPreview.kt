package com.luofelix.laninbox

import android.content.Context
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.ImageView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.luofelix.laninbox.core.network.AttachmentInfo
import com.luofelix.laninbox.feature.inbox.AttachmentTextPreview
import com.luofelix.laninbox.feature.inbox.LocalAttachmentPreviewKind
import com.luofelix.laninbox.feature.inbox.localPreviewKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.text_preview_truncated
import laninbox.shared.generated.resources.text_preview_empty
import org.jetbrains.compose.resources.stringResource

/** Displays received local content; failed decodes return control to the dialog's external-open fallback. */
@Composable
fun AndroidLocalAttachmentPreview(
    attachment: AttachmentInfo,
    loadUri: suspend () -> Uri,
    loadText: suspend () -> AttachmentTextPreview,
    onUnavailable: () -> Unit,
) {
    val unavailable by rememberUpdatedState(onUnavailable)
    val kind = attachment.localPreviewKind()
    if (kind == LocalAttachmentPreviewKind.Text) {
        val result by produceState<AttachmentTextPreview?>(null, attachment) {
            value = try {
                loadText()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                unavailable()
                null
            }
        }
        AppDialogPanel {
            val text = result
            if (text == null) LocalPreviewLoading()
            else {
                SelectionContainer {
                    Text(
                        text.text.ifEmpty { stringResource(Res.string.text_preview_empty) },
                        Modifier.fillMaxWidth(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (text.truncated) {
                    Text(stringResource(Res.string.text_preview_truncated), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        return
    }
    val uri by produceState<Uri?>(null, attachment) {
        value = try {
            loadUri().also { require(it.scheme == "content") }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            unavailable()
            null
        }
    }
    val source = uri
    if (source == null) AppDialogPanel { LocalPreviewLoading() }
    else when (kind) {
        LocalAttachmentPreviewKind.Gif -> AndroidGifPreview(source, attachment.size, unavailable)
        LocalAttachmentPreviewKind.Video, LocalAttachmentPreviewKind.Audio -> {
            AndroidMediaPreview(source, video = kind == LocalAttachmentPreviewKind.Video, onUnavailable = unavailable)
        }
        else -> Unit
    }
}

@Composable
internal fun LocalPreviewLoading() {
    Box(Modifier.fillMaxWidth().height(144.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun AndroidGifPreview(uri: Uri, size: Long, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val unavailable by rememberUpdatedState(onUnavailable)
    if (Build.VERSION.SDK_INT >= 28) {
        val drawable by produceState<Drawable?>(null, uri) {
            value = try {
                withContext(Dispatchers.IO) {
                    ImageDecoder.decodeDrawable(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                        val width = info.size.width
                        val height = info.size.height
                        require(width > 0 && height > 0 && width.toLong() * height <= 40_000_000)
                        decoder.setTargetSampleSize(((maxOf(width, height) + 511) / 512).coerceAtLeast(1))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                unavailable()
                null
            }
        }
        val image = drawable
        AppDialogPanel(padding = 12.dp) {
            if (image == null) LocalPreviewLoading()
            else {
                LifecycleStartEffect(image) {
                    (image as? Animatable)?.start()
                    onStopOrDispose { (image as? Animatable)?.stop() }
                }
                AndroidView(
                    factory = { ImageView(it).apply { scaleType = ImageView.ScaleType.FIT_CENTER; setImageDrawable(image) } },
                    modifier = Modifier.fillMaxWidth().height(224.dp),
                    onReset = null,
                    onRelease = { it.setImageDrawable(null) },
                )
            }
        }
    } else {
        LegacyGifPreview(context, uri, size, unavailable)
    }
}

@Suppress("DEPRECATION")
@Composable
private fun LegacyGifPreview(context: Context, uri: Uri, size: Long, onUnavailable: () -> Unit) {
    val unavailable by rememberUpdatedState(onUnavailable)
    val movie by produceState<Movie?>(null, uri) {
        value = try {
            // Movie decodes eagerly on Android 7/8; bound input to avoid exhausting the app heap.
            require(size <= 16L * 1024 * 1024)
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use(Movie::decodeStream)
                    ?.also { require(it.width().toLong() * it.height() <= 4_000_000) }
                    ?: error("GIF decode failed")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            unavailable()
            null
        }
    }
    val image = movie
    AppDialogPanel(padding = 12.dp) {
        if (image == null) LocalPreviewLoading()
        else {
            val view = remember(context, image) { LegacyGifView(context, image) }
            LifecycleStartEffect(view) {
                view.setRunning(true)
                onStopOrDispose { view.setRunning(false) }
            }
            key(view) {
                AndroidView(
                    factory = { view },
                    modifier = Modifier.fillMaxWidth().height(224.dp),
                    onReset = null,
                    onRelease = { it.setRunning(false) },
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
private class LegacyGifView(context: Context, private val movie: Movie) : View(context) {
    private val started = SystemClock.uptimeMillis()
    private var running = false

    init {
        // Movie drawing requires a software canvas on older Android versions.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    fun setRunning(value: Boolean) {
        running = value
        if (value) postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = minOf(width.toFloat() / movie.width().coerceAtLeast(1), height.toFloat() / movie.height().coerceAtLeast(1))
        val saved = canvas.save()
        canvas.translate((width - movie.width() * scale) / 2, (height - movie.height() * scale) / 2)
        canvas.scale(scale, scale)
        movie.setTime(((SystemClock.uptimeMillis() - started) % (movie.duration().takeIf { it > 0 } ?: 1_000)).toInt())
        movie.draw(canvas, 0f, 0f)
        canvas.restoreToCount(saved)
        if (running) postInvalidateOnAnimation()
    }
}
