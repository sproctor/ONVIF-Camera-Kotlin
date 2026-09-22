package com.seanproctor.onvifcamera

import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import kotlin.io.encoding.Base64

/**
 * HTTP Basic authentication that is offered only to a device that asked for it.
 *
 * ONVIF devices challenge with Digest; Basic is kept for the few that do not. Ktor's own Basic
 * provider cannot be used for that: once every other provider has been tried, the Auth plugin
 * falls back to the last one left whatever the challenge said, and Ktor's provider then sends
 * the credentials regardless. After a Digest exchange rejected for a wrong password, that put
 * the password on the wire in clear text to a device that had never offered Basic. This provider
 * checks the challenge it is handed and stays silent when it is not Basic, so the request goes
 * out unauthenticated, the device answers 401 again, and the caller gets [OnvifUnauthorized].
 */
internal class ChallengedBasicAuthProvider(
    private val username: String,
    private val password: String,
) : AuthProvider {

    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = false

    override fun isApplicable(auth: HttpAuthHeader): Boolean =
        auth.authScheme.equals(AuthScheme.Basic, ignoreCase = true)

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (authHeader == null || !isApplicable(authHeader)) return
        request.headers[HttpHeaders.Authorization] =
            "Basic " + Base64.Default.encode("$username:$password".encodeToByteArray())
    }
}
