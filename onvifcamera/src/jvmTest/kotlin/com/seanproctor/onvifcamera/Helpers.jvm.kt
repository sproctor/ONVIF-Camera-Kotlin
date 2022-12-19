package com.seanproctor.onvifcamera

fun readResourceFile(filename: String): ByteArray {
    return ClassLoader
        .getSystemResourceAsStream(filename)!!
        .readBytes()
}