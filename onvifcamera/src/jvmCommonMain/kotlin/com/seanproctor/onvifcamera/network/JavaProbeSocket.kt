package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_ADDRESS
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_DATAGRAM_SIZE
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_PORT
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.RECEIVE_TIMEOUT_MS
import java.io.IOException
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketException
import java.net.SocketTimeoutException

/** [ProbeSocket] on `java.net`, for the JVM and Android. [onClose] runs once the socket is closed. */
internal class JavaProbeSocket(
    private val logger: OnvifLogger?,
    private val onClose: () -> Unit = {},
) : ProbeSocket {

    // The default multicast route is often not the camera's network (docker
    // and VM bridges, VPNs), so probe on every interface that can multicast.
    // Both are set before the socket is opened, so nothing after the open can fail
    // outside the init block that closes it.
    private val interfaces: List<NetworkInterface> = multicastInterfaces()

    private val multicastAddress: InetAddress = InetAddress.getByName(MULTICAST_ADDRESS)

    private val socket = MulticastSocket(null)

    init {
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
            socket.close()
            throw e
        }
        logger?.debug("Discovery socket bound to port ${socket.localPort}")
    }

    override fun sendProbe(message: ByteArray) {
        val datagram = DatagramPacket(message, message.size, multicastAddress, MULTICAST_PORT)
        if (interfaces.isEmpty()) {
            socket.send(datagram)
            return
        }
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

    override fun receive(): Datagram? {
        val buffer = ByteArray(MULTICAST_DATAGRAM_SIZE)
        val datagram = DatagramPacket(buffer, buffer.size)
        try {
            socket.receive(datagram)
        } catch (_: SocketTimeoutException) {
            return null
        }
        val data = buffer.copyOfRange(datagram.offset, datagram.offset + datagram.length)
        return Datagram(data, checkNotNull(datagram.address.hostAddress))
    }

    override fun close() {
        logger?.debug("Releasing resources")
        try {
            socket.close()
        } finally {
            onClose()
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
}
