package com.seanproctor.onvifcamera

actual fun readResourceFile(filename: String): ByteArray {
    return ClassLoader
        .getSystemResourceAsStream(filename)!!
        .readBytes()
}