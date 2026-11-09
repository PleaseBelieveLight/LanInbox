package com.luofelix.laninbox

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface as ComposeSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import com.luofelix.laninbox.feature.inbox.mediaTimeLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import laninbox.shared.generated.resources.Res
import laninbox.shared.generated.resources.media_pause
import laninbox.shared.generated.resources.media_play
import laninbox.shared.generated.resources.media_seek
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

@Composable
internal fun AndroidMediaPreview(uri: Uri, video: Boolean, onUnavailable: () -> Unit) {
    val context = LocalContext.current
    val unavailable by rememberUpdatedState(onUnavailable)
    val playback = remember(context, uri, video) { LocalMediaPlayback(context, uri, video) { unavailable() } }
    val poster by produceState<Pair<ImageBitmap?, Boolean>>(null to !video, context, uri, video) {
        if (video) {
            value = try {
                withContext(Dispatchers.IO) { loadVideoPoster(context, uri) } to true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Cover extraction is optional; a missing thumbnail must not disable video playback.
                null to true
            }
        }
    }
    val playLabel = stringResource(Res.string.media_play)
    LifecycleStartEffect(playback) {
        playback.prepare()
        // No background playback: release the decoder and audio focus whenever this UI stops.
        onStopOrDispose { playback.release() }
    }
    LaunchedEffect(playback, playback.playing) {
        while (playback.playing) {
            playback.refreshPosition()
            delay(250.milliseconds)
        }
    }
    AppDialogPanel(padding = 12.dp) {
        ComposeSurface(color = Color(0xFF172333), contentColor = Color.White) {
            Box(Modifier.fillMaxWidth().height(if (video) 200.dp else 112.dp), contentAlignment = Alignment.Center) {
                if (video) {
                    key(playback) {
                        AndroidView(
                            factory = { LocalVideoView(it, playback) },
                            modifier = Modifier.matchParentSize(),
                            update = { it.updateVideoSize(); it.keepScreenOn = playback.playing },
                            onReset = null,
                            onRelease = { it.releaseSurface(); it.keepScreenOn = false },
                        )
                    }
                    // Keep the cover until the TextureView reports a real frame, rather than hiding it on Play.
                    if (!playback.videoFrameRendered) {
                        Box(
                            Modifier.matchParentSize().clickable(
                                enabled = playback.ready, role = Role.Button, onClick = playback::toggle,
                            ).semantics { contentDescription = playLabel },
                            contentAlignment = Alignment.Center,
                        ) {
                            poster.first?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Fit) }
                            if (playback.ready && poster.second) {
                                ComposeSurface(shape = CircleShape, color = Color.Black.copy(alpha = 0.5f), contentColor = Color.White) {
                                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                        Icon(AppIcons.Play, null, Modifier.size(26.dp))
                                    }
                                }
                            }
                        }
                    }
                } else Icon(AppIcons.Music, null, Modifier.size(40.dp))
                if (!playback.ready || video && !playback.videoFrameRendered && !poster.second) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        var seeking by remember(playback) { mutableStateOf(false) }
        var seekFraction by remember(playback) { mutableStateOf(0f) }
        val fraction = if (playback.durationMs > 0) playback.positionMs.toFloat() / playback.durationMs else 0f
        val seekLabel = stringResource(Res.string.media_seek)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Slider(
                modifier = Modifier.semantics { contentDescription = seekLabel },
                value = if (seeking) seekFraction else fraction.coerceIn(0f, 1f),
                onValueChange = { seeking = true; seekFraction = it },
                onValueChangeFinished = {
                    playback.seekTo((seekFraction * playback.durationMs).roundToInt())
                    seeking = false
                },
                enabled = playback.ready && playback.durationMs > 0,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(mediaTimeLabel(playback.positionMs), style = MaterialTheme.typography.bodySmall)
                Text(mediaTimeLabel(playback.durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }
        AppDialogButton(
            label = stringResource(if (playback.playing) Res.string.media_pause else Res.string.media_play),
            onClick = playback::toggle,
            modifier = Modifier.fillMaxWidth(),
            icon = if (playback.playing) AppIcons.Pause else AppIcons.Play,
            enabled = playback.ready,
        )
    }
}

/** Owns one local decoder at a time; callbacks and player commands run on the UI thread. */
@Suppress("DEPRECATION")
private class LocalMediaPlayback(
    private val context: Context,
    private val uri: Uri,
    private val video: Boolean,
    private val onUnavailable: () -> Unit,
) {
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0 && playing) pause()
    }
    var ready by mutableStateOf(false)
        private set
    var playing by mutableStateOf(false)
        private set
    var durationMs by mutableIntStateOf(0)
        private set
    var positionMs by mutableIntStateOf(0)
        private set
    var videoWidth by mutableIntStateOf(0)
        private set
    var videoHeight by mutableIntStateOf(0)
        private set
    var videoFrameRendered by mutableStateOf(false)
        private set

    fun prepare() {
        if (player != null) return
        try {
            val media = MediaPlayer()
            player = media
            media.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(if (video) AudioAttributes.CONTENT_TYPE_MOVIE else AudioAttributes.CONTENT_TYPE_MUSIC).build())
            media.setSurface(surface)
            media.setOnPreparedListener {
                if (player === it) {
                    ready = true
                    durationMs = it.duration.coerceAtLeast(0)
                    videoWidth = it.videoWidth
                    videoHeight = it.videoHeight
                    if (positionMs > 0) it.seekTo(positionMs.coerceAtMost(durationMs))
                }
            }
            media.setOnVideoSizeChangedListener { _, width, height -> videoWidth = width; videoHeight = height }
            media.setOnCompletionListener {
                playing = false
                positionMs = durationMs
                audio.abandonAudioFocus(focusListener)
            }
            media.setOnErrorListener { _, _, _ -> fail(); true }
            // The URI belongs to our FileProvider; MediaPlayer never receives a remote URL.
            require(uri.scheme == "content")
            media.setDataSource(context, uri)
            media.prepareAsync()
        } catch (_: Exception) {
            fail()
        }
    }

    fun toggle() {
        val media = player ?: return
        if (!ready) return
        if (playing) pause()
        else try {
            if (audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN) ==
                AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                media.start()
                playing = true
            }
        } catch (_: Exception) {
            fail()
        }
    }

    private fun pause() {
        try {
            if (ready && playing) player?.pause()
            playing = false
            audio.abandonAudioFocus(focusListener)
        } catch (_: Exception) {
            fail()
        }
    }

    fun seekTo(position: Int) {
        if (!ready) return
        try {
            positionMs = position.coerceIn(0, durationMs)
            player?.seekTo(positionMs)
        } catch (_: Exception) {
            fail()
        }
    }

    fun refreshPosition() {
        if (!ready) return
        try {
            positionMs = player?.currentPosition?.coerceAtLeast(0) ?: positionMs
        } catch (_: Exception) {
            fail()
        }
    }

    fun setSurface(value: Surface?) {
        if (surface !== value) videoFrameRendered = false
        surface = value
        try {
            player?.setSurface(value)
        } catch (_: Exception) {
            fail()
        }
    }

    fun onVideoFrameRendered() {
        // Preparation can update an empty texture; retain the cover until playback produces a frame.
        if (ready && playing) videoFrameRendered = true
    }

    fun release() {
        val media = player
        player = null
        if (ready) runCatching { positionMs = media?.currentPosition ?: positionMs }
        ready = false
        playing = false
        videoFrameRendered = false
        media?.setOnErrorListener(null)
        media?.setOnPreparedListener(null)
        media?.setOnCompletionListener(null)
        media?.setOnVideoSizeChangedListener(null)
        media?.release()
        audio.abandonAudioFocus(focusListener)
    }

    private fun fail() {
        release()
        onUnavailable()
    }
}

private class LocalVideoView(context: Context, private val playback: LocalMediaPlayback) : TextureView(context), TextureView.SurfaceTextureListener {
    private var surface: Surface? = null

    init { surfaceTextureListener = this }

    override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(texture)
        playback.setSurface(surface)
        updateVideoSize()
    }

    override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = updateVideoSize()
    override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = playback.onVideoFrameRendered()
    override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
        releaseSurface()
        return true
    }

    fun updateVideoSize() {
        val videoWidth = playback.videoWidth
        val videoHeight = playback.videoHeight
        if (width <= 0 || height <= 0 || videoWidth <= 0 || videoHeight <= 0) return
        val scale = minOf(width.toFloat() / videoWidth, height.toFloat() / videoHeight)
        setTransform(Matrix().apply { setScale(videoWidth * scale / width, videoHeight * scale / height, width / 2f, height / 2f) })
    }

    fun releaseSurface() {
        playback.setSurface(null)
        surface?.release()
        surface = null
    }
}
