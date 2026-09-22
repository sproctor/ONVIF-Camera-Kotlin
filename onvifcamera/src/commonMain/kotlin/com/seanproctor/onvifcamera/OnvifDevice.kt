package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.OnvifCommands.deviceInformationCommand
import com.seanproctor.onvifcamera.OnvifCommands.getSnapshotURICommand
import com.seanproctor.onvifcamera.OnvifCommands.getStreamURICommand
import com.seanproctor.onvifcamera.OnvifCommands.profilesCommand
import com.seanproctor.onvifcamera.OnvifCommands.servicesCommand
import com.seanproctor.onvifcamera.soap.Security
import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * A connected ONVIF device: the handle [requestDevice] returns, holding the services the device
 * advertised, the credentials, and one HTTP client for every later request.
 *
 * Authentication follows the ONVIF Core Specification (5.12). Every authenticated request
 * carries a WS-Security UsernameToken (password digest, fresh nonce, timestamp in the device's
 * own time) in its SOAP header, so a device that checks credentials in SOAP answers in one round
 * trip. A device that authenticates at the HTTP layer instead ignores the header and challenges;
 * the challenge is answered with HTTP Digest, or with Basic if that is what the device asked for.
 *
 * Close the device when done: it owns the HTTP client (or a configuration of the one supplied
 * to [requestDevice]) until then.
 */
public class OnvifDevice internal constructor(
    private val address: Url,
    private val credentials: Credentials?,
    private val namespaceMap: Map<String, String>,
    /** The device clock minus this host's, or null if the device did not report its time. */
    private val clockOffset: Duration?,
    private val client: HttpClient,
    private val logger: OnvifLogger?,
) : AutoCloseable {

    /** Media2 when the device offers it, else Media1, else null; fixed when the device is created. */
    private val media: MediaService? = MediaService.offeredBy(namespaceMap.keys)

    public suspend fun getDeviceInformation(): OnvifDeviceInformation {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetDeviceInformation)
        val response = execute(endpoint, deviceInformationCommand(security()))
        return parseOnvifDeviceInformation(response)
    }

    /**
     * The device's media profiles, from Media2 when it offers that service and from Media1
     * otherwise.
     *
     * @throws OnvifServiceUnavailable if the device offers neither media service
     * @throws OnvifException if the device rejects the request or answers with something else
     */
    public suspend fun getProfiles(): List<MediaProfile> {
        val media = mediaService()
        val response = execute(endpointOf(media), profilesCommand(media, security()))
        return parseOnvifProfiles(media, response)
    }

    /**
     * The RTSP URI for [profile]'s stream, with the host rewritten to the one this device was
     * reached on. Uses the same media service as [getProfiles].
     *
     * @throws OnvifServiceUnavailable if the device offers neither media service
     * @throws OnvifException if the device rejects the request or answers with something else
     */
    public suspend fun getStreamURI(profile: MediaProfile): String {
        val media = mediaService()
        val response = execute(endpointOf(media), getStreamURICommand(media, profile, security = security()))
        return fixHost(parseOnvifStreamUri(media, response))
    }

    /**
     * The HTTP URI of a JPEG snapshot for [profile], with the host rewritten to the one this
     * device was reached on. Uses the same media service as [getProfiles]. A device that cannot
     * supply one answers with a SOAP fault.
     *
     * @throws OnvifServiceUnavailable if the device offers neither media service
     * @throws OnvifException if the device rejects the request or answers with something else
     */
    public suspend fun getSnapshotURI(profile: MediaProfile): String {
        val media = mediaService()
        val response = execute(endpointOf(media), getSnapshotURICommand(media, profile, security()))
        return fixHost(parseOnvifSnapshotUri(media, response))
    }

    /**
     * A snapshot for [profile]: [getSnapshotURI] followed by [getSnapshot] on the result. Ask for
     * the URI once and pass it to [getSnapshot] to poll.
     *
     * @throws OnvifFault if the device offers no snapshot for the profile
     */
    public suspend fun getSnapshot(profile: MediaProfile): ByteArray = getSnapshot(getSnapshotURI(profile))

    /**
     * Fetches the image at [snapshotUri], a URI from [getSnapshotURI], through this device's
     * client, so the camera's HTTP Digest challenge (or Basic, if that is what it asks for) is
     * answered with the device's credentials. WS-Security does not apply here: the snapshot is
     * a plain HTTP resource, not a SOAP operation. Every GET is a fresh frame.
     *
     * @return the image bytes, JPEG whatever the profile's codec
     * @throws OnvifUnauthorized on 401, [OnvifForbidden] on 403
     * @throws OnvifInvalidResponse on any other failure, or a 200 whose body is not an image
     */
    public suspend fun getSnapshot(snapshotUri: String): ByteArray {
        val response = client.get(snapshotUri)
        when (response.status.value) {
            in 200..299 -> Unit
            401 -> throw OnvifUnauthorized("Unauthorized")
            403 -> throw OnvifForbidden("Forbidden")
            else -> throw OnvifInvalidResponse("Invalid response from device: ${response.status}")
        }
        val bytes = response.bodyAsBytes()
        // Some firmware labels the JPEG application/octet-stream, so the bytes get a say too.
        val isImage = response.contentType()?.contentType == "image" || bytes.isJpeg()
        if (!isImage) {
            throw OnvifInvalidResponse(
                "Snapshot response is not an image (${response.contentType() ?: "no content type"}, ${bytes.size} bytes)"
            )
        }
        return bytes
    }

    /** Releases the HTTP client. Further calls on this device fail. */
    override fun close() {
        client.close()
    }

    /** A single-use WS-Security header stamped with the device's time, or null without credentials. */
    private fun security(): Security? =
        credentials?.let { WsSecurity.usernameToken(it.username, it.password, deviceNow()) }

    private fun deviceNow(): Instant = Instant.now().plus(clockOffset ?: Duration.ZERO)

    private suspend fun execute(endpoint: String, body: String): String = client.execute(endpoint, body, clockOffset)

    private fun mediaService(): MediaService = media ?: throw OnvifServiceUnavailable(
        namespace = MediaService.MEDIA2.namespace,
        message = "Device offers neither Media2 (${MediaService.MEDIA2.namespace}) nor Media1 " +
            "(${MediaService.MEDIA1.namespace}), so it has no profiles, stream URIs or snapshot URIs to give",
    )

    private fun endpointOf(media: MediaService): String = buildUrl(namespaceMap.getValue(media.namespace))

    private fun getEndpointForRequest(requestType: OnvifRequestType): String {
        val namespace = requestType.namespace()
        val path = namespaceMap[namespace] ?: throw OnvifServiceUnavailable(namespace)
        return buildUrl(path)
    }

    private fun fixHost(url: String): String {
        return URLBuilder(url).apply {
            host = address.host
        }
            .buildString()
    }

    private fun buildUrl(path: String): String {
        return URLBuilder().apply {
            protocol = address.protocol
            host = address.host
            port = address.port
            encodedPath = path
        }
            .buildString()
    }

    public companion object {
        /**
         * Connects to the device at [url] and returns a handle for later requests; close it when
         * done. Credentials are optional; leave both null for a device that does not require
         * authentication.
         *
         * Two requests are made. `GetSystemDateAndTime`, which needs no credentials, gives the
         * device's clock, so the timestamps in every later WS-Security token are the device's
         * time rather than this host's (devices reject tokens more than a few minutes off).
         * `GetServices` lists what the device offers and decides which media service the handle
         * uses: Media2 if the device offers it, Media1 otherwise. A device offering neither still
         * connects, and its device operations work, but [getProfiles], [getStreamURI] and
         * [getSnapshotURI] throw [OnvifServiceUnavailable].
         *
         * @param httpClient a client to use instead of one created here, for timeouts, proxies
         *   or TLS settings the library does not configure. It is not modified: the device works
         *   with a configuration of it and closing the device does not close it.
         * @throws OnvifException if the device rejects the request or answers with something
         *   that is not a services list
         */
        public suspend fun requestDevice(
            url: String,
            username: String? = null,
            password: String? = null,
            logger: OnvifLogger? = null,
            httpClient: HttpClient? = null,
        ): OnvifDevice {
            val credentials = if (username != null && password != null) Credentials(username, password) else null
            val client = httpClient?.config { configureFor(credentials, logger) }
                ?: HttpClient { configureFor(credentials, logger) }
            try {
                val clockOffset = readClockOffset(client, url, logger)
                val security = credentials?.let {
                    WsSecurity.usernameToken(it.username, it.password, Instant.now().plus(clockOffset ?: Duration.ZERO))
                }
                val result = client.execute(url, servicesCommand(security), clockOffset)
                logger?.debug("Addresses: $result")
                val services = parseOnvifServices(result)
                // Work around bug in some cameras that return the incorrect IP address in the services
                val serviceAddresses = services.associate {
                    val serviceUrl = Url(it.address)
                    it.namespace to serviceUrl.encodedPath
                }
                return OnvifDevice(Url(url), credentials, serviceAddresses, clockOffset, client, logger)
            } catch (t: Throwable) {
                client.close()
                throw t
            }
        }

        public suspend fun isReachableEndpoint(url: String, logger: OnvifLogger? = null): Boolean {
            try {
                HttpClient { configureFor(null, logger) }.use { client ->
                    val response = client.post(url) {
                        contentType(soapContentType)
                        setBody(OnvifCommands.getSystemDateAndTimeCommand)
                    }
                    return response.status.isSuccess()
                }
            } catch (_: IOException) {
                return false
            }
        }

        public suspend fun getHostname(url: String, logger: OnvifLogger? = null): String? {
            HttpClient { configureFor(null, logger) }.use { client ->
                val result = client.execute(url, OnvifCommands.getHostnameCommand, clockOffset = null)
                return parseOnvifGetHostnameResponse(result)
            }
        }

        /**
         * The device clock minus this host's, from `GetSystemDateAndTime`, or null if the device
         * did not answer or did not report UTC time. A failure here is not fatal: tokens are then
         * stamped with this host's time, which is right whenever the clocks agree.
         */
        private suspend fun readClockOffset(client: HttpClient, url: String, logger: OnvifLogger?): Duration? {
            try {
                val response = client.post(url) {
                    contentType(soapContentType)
                    setBody(OnvifCommands.getSystemDateAndTimeCommand)
                }
                val deviceTime = if (response.status.isSuccess()) parseOnvifSystemDateAndTime(response.bodyAsText()) else null
                if (deviceTime == null) {
                    logger?.debug("Device did not report its UTC time; WS-Security timestamps use this host's clock")
                    return null
                }
                return Duration.between(Instant.now(), deviceTime).also { logger?.debug("Device clock offset: $it") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger?.debug("Could not read the device clock: ${e.message}")
                return null
            }
        }

        private suspend fun HttpClient.execute(endpoint: String, body: String, clockOffset: Duration?): String {
            val response = post(endpoint) {
                contentType(soapContentType)
                setBody(body)
            }
            val text = response.bodyAsText()
            // A fault can arrive with any status: the spec wants 400 or 500, some cameras
            // send 200. So every body is checked, and a fault outranks the status.
            val fault = parseOnvifFault(text)
            if (fault != null) throw fault.toException(clockOffset)
            if (response.status.value in 200..299) {
                return text
            }
            throw when (response.status.value) {
                401 -> OnvifUnauthorized("Unauthorized")
                403 -> OnvifForbidden("Forbidden")
                else -> OnvifInvalidResponse("Invalid response from device: ${response.status}")
            }
        }
    }
}

internal class Credentials(val username: String, val password: String)

/** SOI marker followed by any APPn/DQT segment: the start of every JPEG. */
private fun ByteArray.isJpeg(): Boolean =
    size >= 3 && this[0] == 0xFF.toByte() && this[1] == 0xD8.toByte() && this[2] == 0xFF.toByte()

/**
 * A `NotAuthorized` fault is a device saying the credentials are wrong in SOAP rather than in
 * HTTP, so it maps to the same exception a 401 does. When the device clock could not be read,
 * the token timestamp is a second possible cause and the message says so. Every other fault is
 * reported as itself.
 */
private fun OnvifFault.toException(clockOffset: Duration?): OnvifException {
    if ("NotAuthorized" !in subcodes) return this
    val clockHint = if (clockOffset != null) "" else
        " (the device clock could not be read; a device also rejects credentials when its clock and this host's differ by more than a few minutes)"
    return OnvifUnauthorized((message ?: "Not authorized") + clockHint)
}

/** Digest on challenge, Basic only if that is what the device asked for; see [ChallengedBasicAuthProvider]. */
private fun HttpClientConfig<*>.configureFor(credentials: Credentials?, logger: OnvifLogger?) {
    if (credentials != null) {
        install(Auth) {
            providers += ChallengedBasicAuthProvider(credentials.username, credentials.password)
            digest {
                credentials {
                    DigestAuthCredentials(username = credentials.username, password = credentials.password)
                }
            }
        }
    }
    if (logger != null) {
        install(Logging) {
            this.logger = object : Logger {
                override fun log(message: String) {
                    logger.debug(message)
                }
            }
            level = LogLevel.ALL
        }
    }
}

private val soapContentType: ContentType =
    ContentType(
        contentType = "application",
        contentSubtype = "soap+xml",
        parameters = listOf(HeaderValueParam("charset", "utf-8"))
    )
