package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger

/** Specific implementation of [SocketListener] */
internal class JvmSocketListener(
    logger: OnvifLogger?,
) : BaseSocketListener(logger) {
    override fun acquireMulticastLock() {
        // Nothing to do on JVM
    }

    override fun releaseMulticastLock() {
        // Nothing to do on JVM
    }
}
