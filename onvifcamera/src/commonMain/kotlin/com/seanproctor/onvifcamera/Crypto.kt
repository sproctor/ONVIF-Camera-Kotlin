package com.seanproctor.onvifcamera

/** SHA-1 of [parts] concatenated. */
internal expect fun sha1(vararg parts: ByteArray): ByteArray

/** [size] bytes from the platform's cryptographically secure generator. */
internal expect fun secureRandomBytes(size: Int): ByteArray
