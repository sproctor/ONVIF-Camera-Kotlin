package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.OnvifCommands.deviceInformationCommand
import com.seanproctor.onvifcamera.OnvifCommands.getSnapshotURICommand
import com.seanproctor.onvifcamera.OnvifCommands.getStreamURICommand
import com.seanproctor.onvifcamera.OnvifCommands.profilesCommand
import com.seanproctor.onvifcamera.OnvifCommands.servicesCommand
import io.ktor.client.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.io.IOException

/**
 * @author Remy Virin on 04/03/2018.
 * This class represents an ONVIF device and contains the methods to interact with it
 * (getDeviceInformation, getProfiles and getStreamURI).
 * @param username the username to login on the camera
 * @param password the password to login on the camera
 * @param namespaceMap a mapping of SOAP services to paths
 */
public class OnvifDevice internal constructor(
    private val address: Url,
    private val username: String?,
    private val password: String?,
    private val namespaceMap: Map<String, String>,
    private val logger: OnvifLogger?,
) {
    public suspend fun getDeviceInformation(): OnvifDeviceInformation {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetDeviceInformation)
        val response = execute(endpoint, deviceInformationCommand, username, password, logger)
        return parseOnvifDeviceInformation(response)
    }

    /** Media2 when the device offers it, else Media1, else null; fixed when the device is created. */
    private val media: MediaService? = MediaService.offeredBy(namespaceMap.keys)

    /**
     * The device's media profiles, from Media2 when it offers that service and from Media1
     * otherwise.
     *
     * @throws OnvifServiceUnavailable if the device offers neither media service
     * @throws OnvifException if the device rejects the request or answers with something else
     */
    public suspend fun getProfiles(): List<MediaProfile> {
        val media = mediaService()
        val response = execute(endpointOf(media), profilesCommand(media), username, password, logger)
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
        val response = execute(endpointOf(media), getStreamURICommand(media, profile), username, password, logger)
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
        val response = execute(endpointOf(media), getSnapshotURICommand(media, profile), username, password, logger)
        return fixHost(parseOnvifSnapshotUri(media, response))
    }

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
         * Connects to the device at [url], asks it which services it offers and returns a handle
         * for later requests. Credentials are optional; leave both null for a device that does
         * not require authentication.
         *
         * The services list decides which media service the handle uses: Media2 if the device
         * offers it, Media1 otherwise. A device offering neither still connects, and its device
         * operations work, but [getProfiles], [getStreamURI] and [getSnapshotURI] throw
         * [OnvifServiceUnavailable].
         *
         * @throws OnvifException if the device rejects the request or answers with something
         *   that is not a services list
         */
        public suspend fun requestDevice(
            url: String,
            username: String? = null,
            password: String? = null,
            logger: OnvifLogger? = null,
        ): OnvifDevice {
            val result = execute(
                url,
                servicesCommand,
                username,
                password,
                logger,
            )
            logger?.debug("Addresses: $result")
            val services = parseOnvifServices(result)
            // Work around bug in some cameras that return the incorrect IP address in the services
            val serviceAddresses = services.associate {
                val url = Url(it.address)
                it.namespace to url.encodedPath
            }
            return OnvifDevice(Url(url), username, password, serviceAddresses, logger)
        }

        public suspend fun isReachableEndpoint(url: String, logger: OnvifLogger? = null): Boolean {
            try {
                HttpClient {
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
                }.use { client ->
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
            val result = execute(
                url,
                OnvifCommands.getHostnameCommand,
                null,
                null,
                logger,
            )
            return parseOnvifGetHostnameResponse(result)
        }

        internal suspend fun execute(
            endpoint: String,
            body: String,
            username: String?,
            password: String?,
            logger: OnvifLogger?,
        ): String {
            HttpClient {
                if (username != null && password != null) {
                    install(Auth) {
                        // Digest is what ONVIF devices challenge with. Basic is answered only
                        // when a device actually offers it; see ChallengedBasicAuthProvider for
                        // why Ktor's own Basic provider would leak the password otherwise.
                        providers += ChallengedBasicAuthProvider(username, password)
                        digest {
                            credentials {
                                DigestAuthCredentials(username = username, password = password)
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
            }.use { client ->
                val response = client.post(endpoint) {
                    contentType(soapContentType)
                    setBody(body)
                }
                val body = response.bodyAsText()
                // A fault can arrive with any status: the spec wants 400 or 500, some cameras
                // send 200. So every body is checked, and a fault outranks the status.
                val fault = parseOnvifFault(body)
                if (fault != null) throw fault.toException()
                if (response.status.value in 200..299) {
                    return body
                }
                throw when (response.status.value) {
                    401 -> OnvifUnauthorized("Unauthorized")
                    403 -> OnvifForbidden("Forbidden")
                    else -> OnvifInvalidResponse("Invalid response from device: ${response.status}")
                }
            }
        }
    }
}

/**
 * A `NotAuthorized` fault is a device saying the credentials are wrong in SOAP rather than in
 * HTTP, so it maps to the same exception a 401 does. Every other fault is reported as itself.
 */
private fun OnvifFault.toException(): OnvifException =
    if ("NotAuthorized" in subcodes) OnvifUnauthorized(message ?: "Not authorized") else this

private val soapContentType: ContentType =
    ContentType(
        contentType = "application",
        contentSubtype = "soap+xml",
        parameters = listOf(HeaderValueParam("charset", "utf-8"))
    )