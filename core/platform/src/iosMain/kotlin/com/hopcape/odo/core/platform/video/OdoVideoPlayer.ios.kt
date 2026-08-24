package com.hopcape.odo.core.platform.video

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import platform.AVFoundation.AVLayerVideoGravityResizeAspectFill
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerLayer
import platform.AVFoundation.currentItem
import platform.AVFoundation.muted
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.seekToTime
import platform.CoreGraphics.CGRectZero
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSURL
import platform.UIKit.UIView

/**
 * The iOS mirror of the Android player: [AVPlayer] in a [UIView], muted, looping, no
 * transport controls.
 *
 * Looping is a seek back to zero when the item reaches the end — AVFoundation has no repeat
 * mode to set, unlike ExoPlayer.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun OdoVideoPlayer(
    url: String,
    state: OdoVideoState,
    modifier: Modifier,
) {
    // Same rule as Android: a blank URL is "no clip configured", answered here rather than
    // handed to a player that would fail a beat later and flicker through Loading first.
    val nsUrl = remember(url) { url.takeIf { it.isNotBlank() }?.let(NSURL.Companion::URLWithString) }
    if (nsUrl == null) {
        state.status.value = OdoVideoStatus.Failed
        return
    }

    val player = remember(nsUrl) {
        AVPlayer(playerItem = AVPlayerItem(uRL = nsUrl)).apply { muted = true }
    }

    DisposableEffect(player) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = player.currentItem,
            queue = null,
        ) {
            player.seekToTime(CMTimeMake(value = 0, timescale = 1))
            player.play()
        }
        player.play()
        state.status.value = OdoVideoStatus.Playing
        onDispose {
            NSNotificationCenter.defaultCenter.removeObserver(observer)
            player.pause()
        }
    }

    UIKitView(
        factory = { VideoPlayerView(player) },
        modifier = modifier,
    )
}

/**
 * A [UIView] whose only job is to own an [AVPlayerLayer] and keep it the size of the view.
 *
 * `layoutSubviews` is not optional: a CALayer does not follow its parent's bounds, so
 * without it the video keeps whatever frame the view had when it was created — which on a
 * rotation is the wrong one. Same reason `CameraPreviewView` overrides it.
 */
@OptIn(ExperimentalForeignApi::class)
private class VideoPlayerView(player: AVPlayer) : UIView(frame = CGRectZero.readValue()) {

    private val playerLayer = AVPlayerLayer().apply {
        this.player = player
        videoGravity = AVLayerVideoGravityResizeAspectFill
    }

    init {
        layer.addSublayer(playerLayer)
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        playerLayer.setFrame(bounds)
    }
}
