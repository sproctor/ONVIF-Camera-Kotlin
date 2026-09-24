package com.seanproctor.onvifcamera

import java.security.MessageDigest
import java.security.SecureRandom

internal actual fun sha1(vararg parts: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-1").run {
        parts.forEach(::update)
        digest()
    }

private val random = SecureRandom()

internal actual fun secureRandomBytes(size: Int): ByteArray = ByteArray(size).also(random::nextBytes)
