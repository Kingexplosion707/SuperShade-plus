package com.supershade.plus

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * A silent looping video filling its box, cropped to it (the Canvas behind the live activity, the
 * animated cover in it). HLS (the animated covers) and plain MP4 (the Canvas) both play. It fades
 * in once its first frame is up, so the still underneath shows until then, and gives up quietly
 * ([onFailed]) when the stream won't play. It holds its frame while [playing] is false.
 */
@OptIn(UnstableApi::class)
@Composable
fun LoopingVideo(url: String, modifier: Modifier = Modifier, playing: Boolean = true, onFailed: () -> Unit = {}) {
    val context = LocalContext.current
    var aspect by remember(url) { mutableFloatStateOf(0f) }
    var shown by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            setMediaItem(MediaItem.fromUri(url))
            playWhenReady = playing
            prepare()
        }
    }
    // Holds still while the music is paused, so it isn't decoding video for nothing.
    androidx.compose.runtime.LaunchedEffect(player, playing) { player.playWhenReady = playing }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }

            override fun onRenderedFirstFrame() {
                shown = true
            }

            override fun onPlayerError(error: PlaybackException) {
                onFailed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    BoxWithConstraints(modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        // Cover the box: as wide as it and taller, or as tall and wider.
        val a = if (aspect > 0f) aspect else if (boxH > 0f) boxW / boxH else 1f
        val (w, h) = if (boxH <= 0f || boxW / boxH > a) boxW to boxW / a else boxH * a to boxH
        Box(
            Modifier
                .requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() })
                .graphicsLayer { alpha = if (shown) 1f else 0f },
        ) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { player.setVideoTextureView(it) } },
                modifier = Modifier.requiredSize(with(density) { w.toDp() }, with(density) { h.toDp() }),
            )
        }
    }
}
