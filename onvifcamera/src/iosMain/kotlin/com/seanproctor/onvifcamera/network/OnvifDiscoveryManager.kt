package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger

/**
 * The iOS discovery manager. On a device, sending the multicast probe needs the app to hold
 * Apple's `com.apple.developer.networking.multicast` entitlement; every app must declare
 * `NSLocalNetworkUsageDescription`. See [OnvifDiscoveryManager.discoverDevices].
 */
public fun OnvifDiscoveryManager(logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val socketListener = ProbingSocketListener { PosixProbeSocket(logger) }
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}
