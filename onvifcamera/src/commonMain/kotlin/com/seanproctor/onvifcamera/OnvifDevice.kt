package com.seanproctor.onvifcamera

import com.benasher44.uuid.uuid4
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
import io.ktor.network.selector.SelectorManager
import io.ktor.network.sockets.Datagram
import io.ktor.network.sockets.InetSocketAddress
import io.ktor.network.sockets.SocketAddress
import io.ktor.network.sockets.aSocket
import io.ktor.utils.io.core.ByteReadPacket
import io.ktor.utils.io.core.toByteArray
import io.ktor.utils.io.core.use
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

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
            address: String,
            username: String?,
            password: String?,
            debug: Boolean = false,
        ): OnvifDevice {
            val endpoint = if (address.contains(":")) {
                address
            } else {
                "http://$address/onvif/device_service"
            }
            val result = execute(
                endpoint,
                servicesCommand,
                username,
                password,
                debug
            )
            val serviceAddresses = parseOnvifServices(result)
            return OnvifDevice(username, password, serviceAddresses, debug)
        }

        public suspend fun discoverDevices(onDiscover: (DiscoveredOnvifDevice) -> Unit) {
            coroutineScope {
                try {
                    val address = sendProbe()
                    val selectorManager = SelectorManager(Dispatchers.IO)
                    val serverSocket = aSocket(selectorManager).udp().bind(address)

                    val discoveredAddresses = mutableListOf<SocketAddress>()
                    while (true) {
                        val input = serverSocket.incoming.receive()
                        logger?.log("received response")
                        if (!discoveredAddresses.contains(input.address)) {
                            discoveredAddresses.add(input.address)
                            launch {
                                val data = input.packet.readText()
                                val result = parseOnvifProbeResponse(data)
                                if (result.size == 1) {
                                    val probeMatch = result.first()
                                    val xaddrs = probeMatch.xaddrs?.split(" ") ?: emptyList()
                                    for (endpoint in xaddrs) {
                                        if (getSystemDateAndTime(endpoint)) {
                                            onDiscover(
                                                DiscoveredOnvifDevice(
                                                    address = probeMatch.endpointReference.address,
                                                    types = probeMatch.types?.split(" ") ?: emptyList(),
                                                    scopes = probeMatch.scopes?.split(" ") ?: emptyList(),
                                                    uri = endpoint,
                                                )
                                            )
                                            break
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger?.log(e.stackTraceToString())
                }
            }
        }

        private suspend fun sendProbe(): SocketAddress {
            val selectorManager = SelectorManager(Dispatchers.IO)
            val clientSocket = aSocket(selectorManager)
                .udp()
                .connect(
                    remoteAddress = InetSocketAddress("239.255.255.250", 3702),
                    configure = {
                        broadcast = true
                    }
                )
            val messageId = uuid4()
            val data = OnvifCommands.probeCommand(messageId.toString())
            val datagram = Datagram(
                packet = ByteReadPacket(data.toByteArray()),
                address = InetSocketAddress("239.255.255.250", 3702)
            )
            logger?.log("Sending broadcast")
            clientSocket.send(datagram)
            logger?.log("Sent broadcast")
            val address = clientSocket.localAddress
            kotlin.runCatching {
                clientSocket.close()
            }
            return address
        }

        private suspend fun getSystemDateAndTime(url: String): Boolean {
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