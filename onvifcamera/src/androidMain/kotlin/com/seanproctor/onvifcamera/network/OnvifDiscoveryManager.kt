package com.seanproctor.onvifcamera.network

import android.content.Context
import android.net.wifi.WifiManager
import com.seanproctor.onvifcamera.OnvifLogger

public fun OnvifDiscoveryManager(context: Context, logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val socketListener = AndroidSocketListener(wifiManager)
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}