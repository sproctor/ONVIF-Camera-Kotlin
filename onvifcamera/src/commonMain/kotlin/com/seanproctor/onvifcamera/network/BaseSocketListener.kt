package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifCommands
import com.seanproctor.onvifcamera.OnvifLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException
import java.util.UUID
import kotlin.random.Random

/** [SocketListener] on `java.net`; subclasses supply the platform's multicast lock. */
internal abstract class BaseSocketListener(
    private val logger: OnvifLogger?,
) : SocketListener {

    private val multicastAddress: InetAddress by lazy {
        InetAddress.getByName(MULTICAST_ADDRESS)
    }

    override fun listenForPackets(): Flow<DatagramPacket> = flow {
        val socket = openSocket()
        try {
            coroutineScope {
                // Probes are spaced out (see sendProbes), so they go from a child coroutine
                // while this one receives; replies to the first probe are not held up by the
                // schedule. A send failure cancels the scope and fails the flow.
                launch { sendProbes(socket) }

                // receive() blocks and cancellation cannot interrupt it, so the socket has a
                // read timeout: the loop re-checks isActive at least every RECEIVE_TIMEOUT_MS,
                // which bounds how long a cancelled collector's socket stays open.
                while (isActive) {
                    val buffer = ByteArray(MULTICAST_DATAGRAM_SIZE)
                    val datagram = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(datagram)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    emit(datagram)
                }
            }
        } finally {
            closeSocket(socket)
        }
    }.flowOn(Dispatchers.IO)

    private fun openSocket(): MulticastSocket {
        acquireMulticastLock()
        val socket = try {
            MulticastSocket(null)
        } catch (e: Exception) {
            releaseMulticastLock()
            throw e
        }
        try {
            socket.reuseAddress = true
            socket.broadcast = true
            @Suppress("DEPRECATION")
            socket.loopbackMode = true
            // The following isn't available on Android until SDK 33
            // socket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, false)
            socket.soTimeout = RECEIVE_TIMEOUT_MS
            // Probe matches are unicast back to the port the probe was sent from, so
            // use an ephemeral one. Port 3702 itself is usually taken on a desktop
            // (wsdd on Linux, the WSD service on Windows), and the OS hands unicast
            // replies to that more specific binding instead of to us. Joining the
            // group is not needed either: only replies to our own probe are used.
            socket.bind(InetSocketAddress(0))
        } catch (e: Exception) {
            closeSocket(socket)
            throw e
        }
        logger?.debug("Discovery socket bound to port ${socket.localPort}")
        return socket
    }

    private fun closeSocket(socket: MulticastSocket) {
        logger?.debug("Releasing resources")
        releaseMulticastLock()
        if (!socket.isClosed) {
            socket.close()
        }
    }

    /**
     * Sends the probe on the SOAP-over-UDP 1.1 multicast schedule: MULTICAST_UDP_REPEAT
     * retransmissions after the first send, spaced by a random 50–250 ms gap that doubles per
     * repeat and caps at 500 ms. Every repeat is the same message, MessageID included, which is
     * what lets a receiver discard the duplicates; a repeat only matters when the original was
     * lost, and one sent back-to-back would be lost to the same burst. The jitter keeps several
     * clients on one network from retransmitting in lockstep.
     */
    private suspend fun sendProbes(socket: MulticastSocket) {
        val request = OnvifCommands.probeCommand(UUID.randomUUID().toString()).encodeToByteArray()
        val datagram = DatagramPacket(request, request.size, multicastAddress, MULTICAST_PORT)

        // The default multicast route is often not the camera's network (docker
        // and VM bridges, VPNs), so probe on every interface that can multicast.
        val interfaces = multicastInterfaces()
        var gapMs = Random.nextLong(UDP_MIN_DELAY_MS, UDP_MAX_DELAY_MS + 1)
        repeat(1 + MULTICAST_UDP_REPEAT) { attempt ->
            if (attempt > 0) {
                delay(gapMs)
                gapMs = (gapMs * 2).coerceAtMost(UDP_UPPER_DELAY_MS)
            }
            if (interfaces.isEmpty()) {
                socket.send(datagram)
                return@repeat
            }
            // One interface refusing the probe is routine (a VPN tunnel, a bridge with no
            // route). Every interface refusing it means nothing went out, and listening for
            // replies that cannot come would look exactly like an empty network, so fail.
            var sent = false
            var lastError: IOException? = null
            for (networkInterface in interfaces) {
                try {
                    socket.networkInterface = networkInterface
                    socket.send(datagram)
                    sent = true
                } catch (e: IOException) {
                    logger?.debug("Could not probe on ${networkInterface.name}: ${e.message}")
                    lastError = e
                }
            }
            if (!sent) throw checkNotNull(lastError)
        }
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

    protected abstract fun acquireMulticastLock()

    protected abstract fun releaseMulticastLock()

    private companion object {
        const val MULTICAST_DATAGRAM_SIZE = 64 * 1024
        const val MULTICAST_PORT = 3702
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val RECEIVE_TIMEOUT_MS = 500

        // SOAP-over-UDP 1.1 retransmission constants.
        const val MULTICAST_UDP_REPEAT = 2
        const val UDP_MIN_DELAY_MS = 50L
        const val UDP_MAX_DELAY_MS = 250L
        const val UDP_UPPER_DELAY_MS = 500L
    }
}
