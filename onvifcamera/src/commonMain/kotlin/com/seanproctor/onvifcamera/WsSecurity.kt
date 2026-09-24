package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.BASE64_BINARY_ENCODING
import com.seanproctor.onvifcamera.soap.Nonce
import com.seanproctor.onvifcamera.soap.PASSWORD_DIGEST_TYPE
import com.seanproctor.onvifcamera.soap.Password
import com.seanproctor.onvifcamera.soap.Security
import com.seanproctor.onvifcamera.soap.UsernameToken
import kotlin.io.encoding.Base64
import kotlin.time.Instant

/** Builds the WS-Security header ONVIF devices authenticate with. */
internal object WsSecurity {
    /**
     * A fresh UsernameToken: a random 16-byte nonce, [deviceTime] as the `Created` timestamp,
     * and `Base64(SHA-1(nonce + created + password))` as the password digest.
     *
     * Devices reject tokens whose `Created` is more than a few minutes from their own clock, so
     * [deviceTime] must be the device's idea of now, not this host's; see
     * [OnvifDevice.requestDevice] for how it is learnt.
     */
    fun usernameToken(username: String, password: String, deviceTime: Instant): Security {
        val nonce = secureRandomBytes(16)
        // Whole seconds, so the ISO-8601 form has no fraction: 2026-09-22T12:00:00Z.
        val created = Instant.fromEpochSeconds(deviceTime.epochSeconds).toString()
        val digest = sha1(nonce, created.encodeToByteArray(), password.encodeToByteArray())
        return Security(
            UsernameToken(
                username = username,
                password = Password(type = PASSWORD_DIGEST_TYPE, value = Base64.Default.encode(digest)),
                nonce = Nonce(encodingType = BASE64_BINARY_ENCODING, value = Base64.Default.encode(nonce)),
                created = created,
            )
        )
    }
}
