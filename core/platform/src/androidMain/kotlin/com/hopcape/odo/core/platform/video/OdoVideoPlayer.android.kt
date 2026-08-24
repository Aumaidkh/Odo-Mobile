package com.hopcape.odo.core.platform.video

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer behind a [PlayerView] with every control switched off.
 *
 * The player is released in a [DisposableEffect] rather than left to the GC: an ExoPlayer
 * holds a codec and a surface, and onboarding is exactly the screen an owner leaves quickly.
 */
@OptIn(UnstableApi::class)
@Composable
actual fun OdoVideoPlayer(
    url: String,
    state: OdoVideoState,
    modifier: Modifier,
) {
    val context = LocalContext.current

    // A blank URL is the "no clip configured" case, not something to hand to a player —
    // ExoPlayer would report it as a source error a beat later and the screen would flicker
    // through Loading on the way to the same answer.
    if (url.isBlank()) {
        state.status.value = OdoVideoStatus.Failed
        return
    }

    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        state.status.value = OdoVideoStatus.Playing
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    // No network on a first launch lands here, which is the case the
                    // remote-URL decision made possible and the caller has to survive.
                    state.status.value = OdoVideoStatus.Failed
                }
            })
            prepare()
        }
    }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            PlayerView(it).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                this.player = player
            }
        },
    )
}
