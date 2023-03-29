package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import com.seanproctor.onvifcamera.parseOnvifProbeResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.InetAddress

internal class OnvifDiscoveryManagerImpl(
    private val socketListener: SocketListener,
): OnvifDiscoveryManager {
    override fun discoverDevices(retryCount: Int, scope: CoroutineScope): Flow<List<DiscoveredOnvifDevice>> {
        require(retryCount > 0) { "Retry count must be greater than 0" }

        val discoveredDevices = mutableMapOf<InetAddress, DiscoveredOnvifDevice>()
        val mutex = Mutex()
        val packetFlow = socketListener.listenForPackets(retryCount)
        return packetFlow.map { packet ->
            if (!discoveredDevices.containsKey(packet.address)) {
                scope.launch {
                    val data = packet.data.decodeToString()
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
                            mutex.withLock {
                                discoveredDevices.put(packet.address, device)
                            }
                        }
                    } catch (e: Throwable) {
                        println(packet.address)
                        println(data)
                        e.printStackTrace()
                    }
                }
            }
            discoveredDevices.values.toList()
        }
    }
}