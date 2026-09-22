package com.seanproctor.onvifcamera.network

import android.content.Context
import android.net.wifi.WifiManager
import com.seanproctor.onvifcamera.OnvifLogger

/**
 * The Android discovery manager. [context] supplies the [WifiManager] whose multicast lock is
 * held while a discovery flow is collected. See [OnvifDiscoveryManager.discoverDevices] for the
 * `ACCESS_LOCAL_NETWORK` requirement on apps targeting API 37 or higher.
 */
public fun OnvifDiscoveryManager(context: Context, logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val socketListener = AndroidSocketListener(wifiManager)
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}