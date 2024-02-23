package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger

public fun OnvifDiscoveryManager(logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val socketListener = JvmSocketListener(logger)
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}