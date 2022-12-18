package com.seanproctor.onvifcamera

import io.ktor.network.sockets.*
import io.ktor.util.network.*

internal actual fun SocketAddress.getHostName(): String {
    return toJavaAddress().hostname
}