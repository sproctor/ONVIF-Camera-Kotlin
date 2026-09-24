package com.seanproctor.onvifdemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.UIKit.UIView

/**
 * A player for one RTSP URL. iOS has no RTSP player of its own (AVPlayer cannot play one), so
 * the Swift app supplies this, backed by VLCKit.
 */
interface RtspPlayer {
    /** The view the video is drawn into. */
    val view: UIView

    /** Stops playback and releases the stream; the player is not used again. */
    fun stop()
}

/** Makes an [RtspPlayer] that is already playing [url]. Implemented in Swift. */
interface RtspPlayerFactory {
    fun create(url: String): RtspPlayer
}

internal val LocalRtspPlayerFactory = staticCompositionLocalOf<RtspPlayerFactory> {
    error("No RtspPlayerFactory; MainViewController provides one")
}

@Composable
actual fun StreamPlayer(url: String, modifier: Modifier) {
    val factory = LocalRtspPlayerFactory.current
    val player = remember(url) { factory.create(url) }
    UIKitView(
        factory = { player.view },
        modifier = modifier,
        onRelease = { player.stop() },
    )
}
