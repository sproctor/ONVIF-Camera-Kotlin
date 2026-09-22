package com.seanproctor.onvifdemo

import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
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

    // Android 17 blocks local-network UDP for apps targeting API 37 until the user grants
    // ACCESS_LOCAL_NETWORK, and WS-Discovery is local-network UDP. A denial is not fatal here:
    // the discovery flow fails with an IOException, which the view model shows as an error. If
    // the user later revokes the permission, Android restarts the process and this runs again.
    private val requestLocalNetwork =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) Napier.w("Local network permission denied; camera discovery will fail")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = true

        if (Build.VERSION.SDK_INT >= 37 &&
            ContextCompat.checkSelfPermission(this, ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocalNetwork.launch(ACCESS_LOCAL_NETWORK)
        }

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

    private companion object {
        // Manifest.permission.ACCESS_LOCAL_NETWORK, spelled out so the reference does not depend
        // on the compile SDK exposing the constant.
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
