package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.OnvifCommands.deviceInformationCommand
import com.seanproctor.onvifcamera.OnvifCommands.getSnapshotURICommand
import com.seanproctor.onvifcamera.OnvifCommands.getStreamURICommand
import com.seanproctor.onvifcamera.OnvifCommands.profilesCommand
import com.seanproctor.onvifcamera.OnvifCommands.servicesCommand
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HeaderValueParam
import io.ktor.http.contentType
import io.ktor.utils.io.core.use

/**
 * @author Remy Virin on 04/03/2018.
 * This class represents an ONVIF device and contains the methods to interact with it
 * (getDeviceInformation, getProfiles and getStreamURI).
 * @param username the username to login on the camera
 * @param password the password to login on the camera
 * @param namespaceMap a mapping of SOAP services to endpoints
 */
public class OnvifDevice internal constructor(
    public val username: String?,
    public val password: String?,
    public val namespaceMap: Map<String, String>,
    public val debug: Boolean,
) {

    public suspend fun getDeviceInformation(): OnvifDeviceInformation {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetDeviceInformation)
        val response = execute(endpoint, deviceInformationCommand, username, password, debug)
        return parseOnvifDeviceInformation(response)
    }

    public suspend fun getProfiles(): List<MediaProfile> {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetProfiles)
        val response = execute(endpoint, profilesCommand, username, password, debug)
        return parseOnvifProfiles(response)
    }

    public suspend fun getStreamURI(profile: MediaProfile): String {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetStreamURI)
        val response = execute(endpoint, getStreamURICommand(profile), username, password, debug)
        return parseOnvifStreamUri(response)
    }

    public suspend fun getSnapshotURI(profile: MediaProfile): String {
        val endpoint = getEndpointForRequest(OnvifRequestType.GetSnapshotURI)
        val response = execute(endpoint, getSnapshotURICommand(profile), username, password, debug)
        return parseOnvifSnapshotUri(response)
    }

    private fun getEndpointForRequest(requestType: OnvifRequestType): String {
        return namespaceMap[requestType.namespace()] ?: throw OnvifServiceUnavailable()
    }

    public companion object {
        private var logger: Logger? = null

        public fun setLogger(logger: Logger) {
            this.logger = logger
        }

        public suspend fun requestDevice(
            url: String,
            username: String?,
            password: String?,
            debug: Boolean = false,
        ): OnvifDevice {
            val result = execute(
                url,
                servicesCommand,
                username,
                password,
                debug
            )
            val serviceAddresses = parseOnvifServices(result)
            return OnvifDevice(username, password, serviceAddresses, debug)
        }

        public suspend fun isReachableEndpoint(url: String): Boolean {
            HttpClient().use { client ->
                val response = client.post(url) {
                    contentType(soapContentType)
                    setBody(OnvifCommands.getSystemDateAndTimeCommand)
                }
                return response.status.value in 200..299
            }
        }

        internal suspend fun execute(
            endpoint: String,
            body: String,
            username: String?,
            password: String?,
            debug: Boolean,
        ): String {
            HttpClient {
                if (username != null && password != null) {
                    install(Auth) {
                        basic {
                            credentials {
                                BasicAuthCredentials(username = username, password = password)
                            }
                        }
                        customDigest {
                            credentials {
                                DigestAuthCredentials(username = username, password = password)
                            }
                        }
                    }
                }
                if (debug) {
                    install(Logging) {
                        logger = Logger.DEFAULT
                        level = LogLevel.ALL
                    }
                }
            }.use { client ->
                val response = client.post(endpoint) {
                    contentType(soapContentType)
                    setBody(body)
                }
                if (response.status.value in 200..299) {
                    return response.body()
                } else {
                    throw when (response.status.value) {
                        401 -> OnvifUnauthorized("Unauthorized")
                        403 -> OnvifForbidden("Forbidden")
                        else -> OnvifInvalidResponse("Invalid response from device: ${response.status}")
                    }
                }
            }
        }
    }
}

private val soapContentType: ContentType =
    ContentType(
        contentType = "application",
        contentSubtype = "soap+xml",
        parameters = listOf(HeaderValueParam("charset", "utf-8"))
    )