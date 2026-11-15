package com.example.ui.pause

import android.net.Uri
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.example.data.model.PauseBackground
import java.io.File

/**
 * The background video: muted, looping, cropped to fill like a photo.
 *
 * The first frame (saved on import) is shown immediately, and the video fades in over
 * it once its own first frame is ready, so the pause screen never shows a black flash
 * while the player starts. Playback pauses whenever the screen is not visible.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun PauseVideoBackground(
    video: File,
    poster: File?,
    background: PauseBackground,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var aspect by remember(video) {
        mutableFloatStateOf(
            if (background.videoWidth > 0 && background.videoHeight > 0) {
                background.videoWidth.toFloat() / background.videoHeight
            } else 9f / 16f
        )
    }
    var firstFrameShown by remember(video) { mutableStateOf(false) }

    val player = remember(video) {
        ExoPlayer.Builder(context.applicationContext).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(Uri.fromFile(video)))
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrameShown = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width <= 0 || videoSize.height <= 0) return
                var w = videoSize.width * videoSize.pixelWidthHeightRatio
                var h = videoSize.height.toFloat()
                if (videoSize.unappliedRotationDegrees == 90 || videoSize.unappliedRotationDegrees == 270) {
                    val t = w; w = h; h = t
                }
                aspect = w / h
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    // Only plays while visible: no decoding in the background, no battery drain.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> player.play()
                Lifecycle.Event.ON_STOP -> player.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameShown) 1f else 0f,
        animationSpec = tween(250),
        label = "videoFadeIn"
    )

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val canvasWidthPx = constraints.maxWidth.toFloat()
        val canvasHeightPx = constraints.maxHeight.toFloat()
        val boxAspect = maxWidth / maxHeight
        // Size the video so it covers the whole screen, like a centre-cropped photo.
        val videoWidth = if (aspect > boxAspect) maxHeight * aspect else maxWidth
        val videoHeight = if (aspect > boxAspect) maxHeight else maxWidth / aspect

        val transform = Modifier.graphicsLayer {
            scaleX = background.photoScale
            scaleY = background.photoScale
            translationX = background.photoOffsetX * canvasWidthPx
            translationY = background.photoOffsetY * canvasHeightPx
        }

        if (poster != null) {
            AsyncImage(
                model = poster,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(transform)
            )
        }

        key(video) {
            AndroidView(
                // TextureView, not SurfaceView: it scales and fades with the rest of
                // the screen, including inside the editor's shrunken preview.
                factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                modifier = Modifier
                    .align(Alignment.Center)
                    .requiredSize(videoWidth, videoHeight)
                    .then(transform)
                    .graphicsLayer { alpha = videoAlpha }
            )
        }

        // Sits over the video view so touches reach the editor's drag and pinch
        // handlers instead of the Android view underneath.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { }
        )
    }
}
