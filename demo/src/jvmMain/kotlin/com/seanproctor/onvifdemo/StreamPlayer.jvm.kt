package com.seanproctor.onvifdemo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@Composable
actual fun StreamPlayer(url: String, modifier: Modifier) {
    val state = rememberVideoPlayerState()
    VideoPlayer(
        url = url,
        state = state,
        modifier = modifier,
        onFinish = {
            println("Video finished playing")
        },
    )
    LaunchedEffect(state.isResumed) {
        println("isResumed: ${state.isResumed}")
    }
}