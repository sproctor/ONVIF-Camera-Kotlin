package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger

/**
 * The iOS discovery manager. Sending the multicast probe needs the app to hold Apple's
 * `com.apple.developer.networking.multicast` entitlement and to declare
 * `NSLocalNetworkUsageDescription`; see [OnvifDiscoveryManager.discoverDevices].
 */
public fun OnvifDiscoveryManager(logger: OnvifLogger? = null): OnvifDiscoveryManager {
    val socketListener = ProbingSocketListener { PosixProbeSocket(logger) }
    return OnvifDiscoveryManagerImpl(socketListener, logger)
}
