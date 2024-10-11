package com.seanproctor.onvifdemo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ivanempire.lighthouse.lighthouseClient
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

fun main() {
    val lighthouseClient = lighthouseClient()
    Napier.base(DebugAntilog())
    val logger = object : OnvifLogger {
        override fun error(message: String) {
            Napier.e(message)
        }

        override fun error(message: String, e: Throwable) {
            Napier.e(message, e)
        }

        override fun debug(message: String) {
            Napier.d(message)
        }
    }
    val onvifDiscoveryManager = OnvifDiscoveryManager(logger)
    val viewModel = MainViewModel(lighthouseClient, onvifDiscoveryManager, logger)
    application {
        Window(
            title = "ONVIF Camera Demo",
            onCloseRequest = ::exitApplication
        ) {
            MainContent(viewModel)
        }
    }
}