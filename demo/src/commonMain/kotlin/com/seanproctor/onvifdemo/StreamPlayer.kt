package com.seanproctor.onvifdemo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun StreamPlayer(url: String, modifier: Modifier = Modifier)
