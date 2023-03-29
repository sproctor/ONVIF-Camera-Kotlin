package com.seanproctor.onvifcamera.network

import android.content.Context
import android.net.wifi.WifiManager

public fun OnvifDiscoveryManager(context: Context): OnvifDiscoveryManager {
    val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val socketListener = AndroidSocketListener(wifiManager)
    return OnvifDiscoveryManagerImpl(socketListener)
}