package com.seanproctor.onvifdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier

/**
 * Main activity of this demo project. It allows the user to type his camera IP address,
 * login and password.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Napier.base(DebugAntilog())
        val logger = object : OnvifLogger {

            override fun error(message: String, e: Throwable?) {
                Napier.e(message, e)
            }

            override fun debug(message: String) {
                Napier.d(message)
            }
        }

        val onvifDiscoveryManager = OnvifDiscoveryManager(
            context = this,
            logger = logger,
        )

        setContent {
            val viewModel: MainViewModel = viewModel<MainViewModel> {
                MainViewModel(onvifDiscoveryManager, logger)
            }
            MainContent(viewModel)
        }
    }
}
