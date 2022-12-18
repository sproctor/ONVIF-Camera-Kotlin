package com.seanproctor.onvifdemo

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.seanproctor.onvifcamera.OnvifDevice
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.*

fun main(args: Array<String>) {
    val viewModel = MainViewModel()
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