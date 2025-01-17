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
                                    addresses = probeMatch.xaddrs?.split(" ")
                                        ?: emptyList(),
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