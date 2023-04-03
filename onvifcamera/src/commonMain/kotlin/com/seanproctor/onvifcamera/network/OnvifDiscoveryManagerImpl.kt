package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.DiscoveredOnvifDevice
import com.seanproctor.onvifcamera.parseOnvifProbeResponse
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.InetAddress

internal class OnvifDiscoveryManagerImpl(
    private val socketListener: SocketListener,
): OnvifDiscoveryManager {
    override fun discoverDevices(retryCount: Int, scope: CoroutineScope): Flow<List<DiscoveredOnvifDevice>> {
        require(retryCount > 0) { "Retry count must be greater than 0" }

        val discoveredDevices = MutableStateFlow(persistentHashMapOf<InetAddress, DiscoveredOnvifDevice>())
        val job = scope.launch {
            socketListener.listenForPackets(retryCount)
                .collect { packet ->
                    launch {
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
                                discoveredDevices.update { deviceMap ->
                                    deviceMap.mutate {
                                        it[packet.address] = device
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            e.printStackTrace()
                        }
                    }
                }
        }
        return discoveredDevices
            .map { it.values.toList() }
            .onCompletion { job.cancel() }
    }
}