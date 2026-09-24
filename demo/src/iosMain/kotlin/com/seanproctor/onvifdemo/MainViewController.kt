package com.seanproctor.onvifdemo

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import platform.UIKit.UIViewController

/** The iOS entry point, called from the Swift app with the VLCKit player it provides. */
@Suppress("FunctionName", "unused") // Called from Swift
fun MainViewController(playerFactory: RtspPlayerFactory): UIViewController {
    Napier.base(DebugAntilog())
    val logger = object : OnvifLogger {
        override fun error(message: String, e: Throwable?) {
            Napier.e(message, e)
        }

        override fun debug(message: String) {
            Napier.d(message)
        }
    }
    // Discovery needs the multicast entitlement (see iosDemo/README.md); without it the Scan
    // button reports the failure and connecting by address still works.
    val onvifDiscoveryManager = OnvifDiscoveryManager(logger)
    return ComposeUIViewController {
        CompositionLocalProvider(LocalRtspPlayerFactory provides playerFactory) {
            val viewModel = viewModel { MainViewModel(onvifDiscoveryManager, logger) }
            MainContent(viewModel)
        }
    }
}
