package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.parseOnvifProbeResponse
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.InetAddress

internal class OnvifDiscoveryManagerImpl(
    private val socketListener: SocketListener,
    private val logger: OnvifLogger?,
): OnvifDiscoveryManager {
    override fun discoverDevices(retryCount: Int, scope: CoroutineScope): Flow<List<DiscoveredOnvifDevice>> {
        require(retryCount >= 0) { "Retry count cannot be negative" }

        val discoveredDevices = MutableStateFlow(persistentHashMapOf<InetAddress, DiscoveredOnvifDevice>())
        val job = scope.launch {
            socketListener.listenForPackets(retryCount)
                .catch { cause ->
                    logger?.error("Error listening for devices", cause)
                }
                .collect { packet: DatagramPacket ->
                    launch {
                        val data = packet.data.decodeToString(
                            startIndex = packet.offset,
                            endIndex = packet.offset + packet.length,
                        )
                        try {
                            val result = parseOnvifProbeResponse(data)
                            if (result.size == 1) {
                                val probeMatch = result.first()
                                val device = DiscoveredOnvifDevice(
                                    id = probeMatch.endpointReference.address,
                                    types = probeMatch.types?.split(" ") ?: emptyList(),
                                    scopes = probeMatch.scopes?.split(" ") ?: emptyList(),
                                    addresses = withSenderAddress(
                                        xaddrs = probeMatch.xaddrs?.split(" ")?.filter { it.isNotBlank() }
                                            ?: emptyList(),
                                        senderHost = packet.address.hostAddress,
                                    ),
                                )
                                discoveredDevices.update { deviceMap ->
                                    deviceMap.mutate {
                                        it[packet.address] = device
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            logger?.error("Error parsing probe response: $data", e)
                        }
                    }
                }
        }
        return discoveredDevices
            .map { it.values.toList() }
            .onCompletion { job.cancel() }
    }
}

/**
 * Some cameras (seen on a Lorex LNB45ABB) keep advertising the address they had
 * before their DHCP lease changed. The probe match still arrives from the address
 * the camera really has, so for every XAddr naming another host, the same URL on
 * the sender's address is offered first.
 */
internal fun withSenderAddress(xaddrs: List<String>, senderHost: String?): List<String> {
    // IPv6 senders would need brackets and a scope; discovery here is IPv4 only.
    if (senderHost == null || ':' in senderHost) return xaddrs
    val corrected = xaddrs.mapNotNull { xaddr ->
        val match = XADDR_HOST.find(xaddr) ?: return@mapNotNull null
        if (match.groupValues[2] == senderHost) null
        else xaddr.replaceRange(match.groups[2]!!.range, senderHost)
    }
    return (corrected + xaddrs).distinct()
}

// scheme://[userinfo@]host — the host of an IPv4 or named XAddr.
private val XADDR_HOST = Regex("""^(\w+://(?:[^/@]*@)?)([^/:\[\]]+)""")
