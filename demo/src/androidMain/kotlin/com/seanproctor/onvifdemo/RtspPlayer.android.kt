package com.seanproctor.onvifdemo

import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import io.ktor.http.*

@OptIn(UnstableApi::class)
@Composable
actual fun RtspPlayer(rtspUrl: String, username: String?, password: String?) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build()
            .apply {
                playWhenReady = true
            }
    }

    DisposableEffect(Unit) {
        val urlWithCredentials = URLBuilder(rtspUrl).apply {
            user = username
            this.password = password
        }
            .buildString()

        Log.d("RtspPlayer", "urlWithCredentials: $urlWithCredentials")

        val mediaItem = MediaItem.fromUri(urlWithCredentials)
        val mediaSource = RtspMediaSource.Factory().createMediaSource(mediaItem)

        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
            }
        }
    )
}
