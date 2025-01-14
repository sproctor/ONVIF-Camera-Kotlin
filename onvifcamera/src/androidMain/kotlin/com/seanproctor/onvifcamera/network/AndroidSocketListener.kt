package com.seanproctor.onvifcamera.network

import android.net.wifi.WifiManager
import com.seanproctor.onvifcamera.OnvifLogger

/** Specific implementation of [SocketListener] */
internal class AndroidSocketListener(
    private val wifiManager: WifiManager,
    logger: OnvifLogger? = null,
) : BaseSocketListener(logger) {

    private val multicastLock: WifiManager.MulticastLock by lazy {
        wifiManager.createMulticastLock("OnvifCamera")
    }

    override fun acquireMulticastLock() {
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()
    }

    override fun releaseMulticastLock() {
        if (multicastLock.isHeld) {
            multicastLock.release()
        }
    }
}
