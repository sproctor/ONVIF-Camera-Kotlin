package com.seanproctor.onvifdemo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ivanempire.lighthouse.LighthouseClient
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.Logger
import org.slf4j.LoggerFactory

fun main() {
    val lighthouseClient = LighthouseClient()
    val onvifDiscoveryManager = OnvifDiscoveryManager(LoggerFactory.getLogger("OnvifCamera"))
    val viewModel = MainViewModel(lighthouseClient, onvifDiscoveryManager)
    println("starting")
    Napier.base(DebugAntilog())
    OnvifDevice.setLogger(
        object : Logger {
            override fun log(message: String) {
                Napier.i(message)
            }
        }
    )
    application {
        Window(
            title = "ONVIF Camera Demo",
            onCloseRequest = ::exitApplication
        ) {
            MainContent(viewModel)
        }
    }
}