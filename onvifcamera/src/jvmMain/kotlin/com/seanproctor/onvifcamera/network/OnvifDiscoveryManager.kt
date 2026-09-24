package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger

public fun OnvifDiscoveryManager(logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val socketListener = ProbingSocketListener { JavaProbeSocket(logger) }
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}
