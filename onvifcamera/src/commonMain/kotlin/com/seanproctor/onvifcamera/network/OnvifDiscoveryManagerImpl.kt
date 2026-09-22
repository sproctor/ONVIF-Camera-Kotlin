package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.parseOnvifProbeResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.runningFold
import java.net.DatagramPacket
import java.net.InetAddress

internal class OnvifDiscoveryManagerImpl(
    private val socketListener: SocketListener,
    private val logger: OnvifLogger?,
) : OnvifDiscoveryManager {
    override fun discoverDevices(retryCount: Int): Flow<List<DiscoveredOnvifDevice>> {
        require(retryCount >= 0) { "Retry count cannot be negative" }

        return socketListener.listenForPackets(retryCount)
            .mapNotNull { packet -> parseProbeMatch(packet) }
            // Parsing joins the socket work on IO. This also puts a channel between the receive
            // loop and the parser, so a slow parse cannot stall receive() and let the kernel
            // drop datagrams.
            .flowOn(Dispatchers.IO)
            // The accumulator is an immutable map, so every emission is a stable snapshot. The
            // LinkedHashMap copy keeps discovery order and leaves a camera that answers again
            // in its original position. A camera answering a retry identically yields an equal
            // map, which distinctUntilChanged drops.
            .runningFold(emptyMap<InetAddress, DiscoveredOnvifDevice>()) { devices, (address, device) ->
                devices + (address to device)
            }
            .distinctUntilChanged()
            .map { it.values.toList() }
            .catch { cause ->
                logger?.error("Discovery failed", cause)
                throw cause
            }
    }

    /** The device a probe match describes, keyed by its sender, or null if this is not one. */
    private fun parseProbeMatch(packet: DatagramPacket): Pair<InetAddress, DiscoveredOnvifDevice>? {
        val data = packet.data.decodeToString(
            startIndex = packet.offset,
            endIndex = packet.offset + packet.length,
        )
        return try {
            val probeMatch = parseOnvifProbeResponse(data).singleOrNull() ?: return null
            val device = DiscoveredOnvifDevice(
                id = probeMatch.endpointReference.address,
                types = probeMatch.types?.split(" ") ?: emptyList(),
                scopes = probeMatch.scopes?.split(" ") ?: emptyList(),
                addresses = withSenderAddress(
                    xaddrs = probeMatch.xaddrs?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                    senderHost = packet.address.hostAddress,
                ),
            )
            packet.address to device
        } catch (e: Exception) {
            logger?.error("Error parsing probe response: $data", e)
            null
        }
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
