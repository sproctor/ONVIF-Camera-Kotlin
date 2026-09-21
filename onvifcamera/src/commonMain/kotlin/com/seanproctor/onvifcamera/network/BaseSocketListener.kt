package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifCommands
import com.seanproctor.onvifcamera.OnvifLogger
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.util.UUID

/** Specific implementation of [SocketListener] */
internal abstract class BaseSocketListener(
    private val logger: OnvifLogger?,
) : SocketListener {

    private val multicastAddress: InetAddress by lazy {
        InetAddress.getByName(MULTICAST_ADDRESS)
    }

    override fun setupSocket(): MulticastSocket {
        acquireMulticastLock()

        val multicastSocket = MulticastSocket(null)
        multicastSocket.reuseAddress = true
        multicastSocket.broadcast = true
        @Suppress("DEPRECATION")
        multicastSocket.loopbackMode = true
        // The following isn't available on Android until SDK 33
        // multicastSocket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false)

        try {
            // Probe matches are unicast back to the port the probe was sent from, so
            // use an ephemeral one. Port 3702 itself is usually taken on a desktop
            // (wsdd on Linux, the WSD service on Windows), and the OS hands unicast
            // replies to that more specific binding instead of to us. Joining the
            // group is not needed either: only replies to our own probe are used.
            multicastSocket.bind(InetSocketAddress(0))
            logger?.debug("MulticastSocket has been setup")
        } catch (ex: Exception) {
            logger?.error("Could finish setting up the multicast socket and group", ex)
        }

        return multicastSocket
    }

    override fun listenForPackets(retryCount: Int): Flow<DatagramPacket> {
        logger?.debug("Setting up datagram packet flow")
        val multicastSocket = setupSocket()

        return flow {
            val messageId = UUID.randomUUID()
            val requestMessage = OnvifCommands.probeCommand(messageId.toString()).toByteArray()
            val requestDatagram = DatagramPacket(
                requestMessage,
                requestMessage.size,
                multicastAddress,
                MULTICAST_PORT
            )

            // The default multicast route is often not the camera's network (docker
            // and VM bridges, VPNs), so probe on every interface that can multicast.
            val interfaces = multicastInterfaces()
            repeat(1 + retryCount) {
                if (interfaces.isEmpty()) {
                    if (!multicastSocket.isClosed) multicastSocket.send(requestDatagram)
                }
                for (networkInterface in interfaces) {
                    if (multicastSocket.isClosed) break
                    try {
                        multicastSocket.networkInterface = networkInterface
                        multicastSocket.send(requestDatagram)
                    } catch (e: IOException) {
                        logger?.debug("Could not probe on ${networkInterface.name}: ${e.message}")
                    }
                }
            }

            while (currentCoroutineContext().isActive && !multicastSocket.isClosed) {
                val discoveryBuffer = ByteArray(MULTICAST_DATAGRAM_SIZE)
                val discoveryDatagram = DatagramPacket(discoveryBuffer, discoveryBuffer.size)
                multicastSocket.receive(discoveryDatagram)

                emit(discoveryDatagram)
            }
        }
            .catch { cause -> logger?.error("Error during discovery", cause) }
            .onCompletion { teardownSocket(multicastSocket) }
    }

    private fun multicastInterfaces(): List<NetworkInterface> =
        try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .filter { it.isUp && !it.isLoopback && it.supportsMulticast() }
                .filter { networkInterface -> networkInterface.inetAddresses.asSequence().any { it is Inet4Address } }
                .toList()
        } catch (e: SocketException) {
            logger?.error("Could not list network interfaces", e)
            emptyList()
        }

    override fun teardownSocket(multicastSocket: MulticastSocket) {
        logger?.debug("Releasing resources")

        releaseMulticastLock()

        if (!multicastSocket.isClosed) {
            multicastSocket.close()
        }
    }

    protected abstract fun acquireMulticastLock()

    protected abstract fun releaseMulticastLock()

    private companion object {
        const val MULTICAST_DATAGRAM_SIZE = 64 * 1024
        const val MULTICAST_PORT = 3702
        const val MULTICAST_ADDRESS = "239.255.255.250"
    }
}
