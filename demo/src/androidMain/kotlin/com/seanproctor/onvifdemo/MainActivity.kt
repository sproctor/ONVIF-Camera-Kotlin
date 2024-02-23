package com.seanproctor.onvifdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivanempire.lighthouse.LighthouseClient
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import dev.icerock.moko.mvvm.createViewModelFactory
import dev.icerock.moko.mvvm.viewmodel.ViewModel
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

        val lighthouseClient = LighthouseClient(this)
        val onvifDiscoveryManager = OnvifDiscoveryManager(
            context = this,
            logger = logger,
        )

        setContent {
            val viewModel: MainViewModel = viewModel<ViewModel>(
                factory = createViewModelFactory {
                    MainViewModel(lighthouseClient, onvifDiscoveryManager, logger) as ViewModel
                }
            ) as MainViewModel
            MainContent(viewModel)
        }
    }
}
