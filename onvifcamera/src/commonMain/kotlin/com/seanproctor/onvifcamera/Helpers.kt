package com.seanproctor.onvifcamera

import io.ktor.network.sockets.*

internal expect fun SocketAddress.getHostName(): String