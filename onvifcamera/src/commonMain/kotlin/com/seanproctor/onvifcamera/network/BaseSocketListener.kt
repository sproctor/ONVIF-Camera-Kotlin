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
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
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
            multicastSocket.joinGroup(InetSocketAddress(multicastAddress, 0), null)
            multicastSocket.bind(InetSocketAddress(MULTICAST_PORT))
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

            repeat(1 + retryCount) {
                if (!multicastSocket.isClosed) {
                    multicastSocket.send(requestDatagram)
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

    override fun teardownSocket(multicastSocket: MulticastSocket) {
        logger?.debug("Releasing resources")

        releaseMulticastLock()

        if (!multicastSocket.isClosed) {
            multicastSocket.leaveGroup(InetSocketAddress(multicastAddress, 0), null)
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
