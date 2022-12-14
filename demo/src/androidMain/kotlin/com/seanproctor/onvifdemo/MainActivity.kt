package com.seanproctor.onvifdemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seanproctor.onvifcamera.OnvifDevice
import dev.icerock.moko.mvvm.createViewModelFactory
import io.github.aakira.napier.Napier
import io.ktor.client.plugins.logging.*

/**
 * Main activity of this demo project. It allows the user to type his camera IP address,
 * login and password.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OnvifDevice.setLogger(
            object : Logger {
                override fun log(message: String) {
                    Napier.i(message)
                }
            }
        )

        setContent {
            val viewModel: MainViewModel = viewModel<MainViewModel>(
                factory = createViewModelFactory { MainViewModel() }
            )
            MainContent(viewModel)
        }
    }
}