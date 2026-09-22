package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.BASE64_BINARY_ENCODING
import com.seanproctor.onvifcamera.soap.Nonce
import com.seanproctor.onvifcamera.soap.PASSWORD_DIGEST_TYPE
import com.seanproctor.onvifcamera.soap.Password
import com.seanproctor.onvifcamera.soap.Security
import com.seanproctor.onvifcamera.soap.UsernameToken
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.io.encoding.Base64

/** Builds the WS-Security header ONVIF devices authenticate with. */
internal object WsSecurity {
    private val random = SecureRandom()

    /**
     * A fresh UsernameToken: a random 16-byte nonce, [deviceTime] as the `Created` timestamp,
     * and `Base64(SHA-1(nonce + created + password))` as the password digest.
     *
     * Devices reject tokens whose `Created` is more than a few minutes from their own clock, so
     * [deviceTime] must be the device's idea of now, not this host's; see
     * [OnvifDevice.requestDevice] for how it is learnt.
     */
    @Suppress("NewApi") // java.time is API 26; the README requires minSdk 26 or desugaring
    fun usernameToken(username: String, password: String, deviceTime: Instant): Security {
        val nonce = ByteArray(16).also(random::nextBytes)
        val created = DateTimeFormatter.ISO_INSTANT.format(deviceTime.truncatedTo(ChronoUnit.SECONDS))
        val digest = MessageDigest.getInstance("SHA-1").run {
            update(nonce)
            update(created.encodeToByteArray())
            update(password.encodeToByteArray())
            digest()
        }
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
