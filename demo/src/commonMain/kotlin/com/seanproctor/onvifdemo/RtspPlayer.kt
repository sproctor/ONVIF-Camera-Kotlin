package com.seanproctor.onvifdemo

import androidx.compose.runtime.Composable

@Composable
expect fun RtspPlayer(rtspUrl: String, username: String?, password: String?)
