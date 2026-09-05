package com.armsx2.ui.premium

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.armsx2.art.ArcadeMedia
import com.armsx2.EmuState
import com.armsx2.runtime.MainActivityRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The System 246/256 menu film, looping behind the launcher.
 *
 * A TextureView, not a SurfaceView: a SurfaceView is a separate window punched through the one
 * the app draws into, so it cannot be made translucent and cannot have UI composited over it with
 * alpha. A TextureView is an ordinary view holding a GL texture, which is exactly what is needed
 * for a background that the whole launcher sits on top of.
 *
 * Silent and looping, and it does not start until the file is on disk — the video is 27 MB, so it
 * is fetched once from the media repository rather than shipped in the APK. Until then this draws
 * nothing at all and the aurora behind it is the background, which is also what happens with no
 * network. The film is decoration; nothing here is allowed to be a reason the launcher looks
 * broken.
 */
@Composable
fun MenuVideoBackground(modifier: Modifier = Modifier, alpha: Float = 0.30f) {
    val context = LocalContext.current
    var file by remember { mutableStateOf<File?>(null) }

    // Never while a game is up. Decoding a looping 1080p film behind a screen nobody is looking at
    // costs a core and a slice of the GPU on a device that is, by the time anyone notices, already
    // short of both. Returning here disposes the player rather than pausing it, so it gives the
    // codec back too -- Android has few of those and they are shared with the emulator.
    val playing = MainActivityRuntime.eState.value == EmuState.RUNNING ||
        MainActivityRuntime.eState.value == EmuState.PAUSED
    if (playing) return

    var fetching by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Off the main thread: the first run downloads 27 MB.
        fetching = true
        file = withContext(Dispatchers.IO) { runCatching { ArcadeMedia.menuVideo(context) }.getOrNull() }
        fetching = false
        failed = file == null
    }

    val path = file
    if (path == null) {
        // Say which of the two nothings this is. A 27 MB download over a phone connection takes a
        // while, and until now the wait and a failure looked identical -- an empty background,
        // with no way to tell whether to keep waiting.
        if (fetching || failed) {
            Box(modifier, contentAlignment = Alignment.BottomStart) {
                Text(
                    if (fetching) "Baixando o fundo do menu…" else "Não foi possível baixar o fundo do menu.",
                    color = Palette.labelTertiary,
                    style = Type.caption,
                    modifier = Modifier.padding(start = 34.dp, bottom = 6.dp),
                )
            }
        }
        return
    }

    // One player for the life of this composable. Held outside AndroidView's factory so the
    // DisposableEffect below can release it — a MediaPlayer left running holds a codec, and
    // Android has a small, shared number of those.
    val player = remember(path) { MediaPlayer() }

    DisposableEffect(player) {
        onDispose {
            runCatching { player.stop() }
            runCatching { player.release() }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextureView(ctx).apply {
                isOpaque = false
                this.alpha = alpha
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            player.reset()
                            player.setDataSource(path.absolutePath)
                            player.setSurface(Surface(st))
                            player.isLooping = true
                            player.setVolume(0f, 0f) // menu film, not a soundtrack
                            player.setOnPreparedListener { mp ->
                                crop(this@apply, mp.videoWidth, mp.videoHeight)
                                runCatching { mp.start() }
                            }
                            player.prepareAsync()
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                        crop(this@apply, player.videoWidth, player.videoHeight)
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        runCatching { player.stop() }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        update = { it.alpha = alpha },
    )
}

/**
 * Fill the view, keeping the film's shape.
 *
 * A TextureView stretches its content to its own bounds, which on a phone that is not the video's
 * aspect ratio distorts every face in it. Scaling the larger axis up and centring gives the
 * cover-crop this wants; there is nothing to see at the edges of a background.
 */
private fun crop(view: TextureView, videoW: Int, videoH: Int) {
    if (videoW <= 0 || videoH <= 0 || view.width <= 0 || view.height <= 0) return
    val viewW = view.width.toFloat()
    val viewH = view.height.toFloat()
    val scale = maxOf(viewW / videoW, viewH / videoH)
    val drawnW = videoW * scale
    val drawnH = videoH * scale
    val matrix = Matrix().apply {
        setScale(drawnW / viewW, drawnH / viewH)
        postTranslate((viewW - drawnW) / 2f, (viewH - drawnH) / 2f)
    }
    view.setTransform(matrix)
}
