package com.seanproctor.onvifdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ivanempire.lighthouse.LighthouseClient
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import dev.icerock.moko.mvvm.createViewModelFactory
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.Logger

/**
 * Main activity of this demo project. It allows the user to type his camera IP address,
 * login and password.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Napier.base(DebugAntilog())

        OnvifDevice.setLogger(
            object : Logger {
                override fun log(message: String) {
                    Napier.i(message)
                }
            }
        )

        val lighthouseClient = LighthouseClient(this)
        val onvifDiscoveryManager = OnvifDiscoveryManager(this)

        setContent {
            val viewModel: MainViewModel = viewModel<ViewModel>(
                factory = createViewModelFactory { MainViewModel(lighthouseClient, onvifDiscoveryManager) as ViewModel }
            ) as MainViewModel
            MainContent(viewModel)
        }
    }
}
