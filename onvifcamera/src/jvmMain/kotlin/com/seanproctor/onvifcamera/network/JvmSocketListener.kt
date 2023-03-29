package com.seanproctor.onvifcamera.network

import com.benasher44.uuid.uuid4
import com.seanproctor.onvifcamera.OnvifCommands
import io.ktor.utils.io.core.toByteArray
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import org.slf4j.Logger

/** Specific implementation of [SocketListener] */
internal class JvmSocketListener(
    private val logger: Logger?,
) : SocketListener {

    private val multicastAddress: InetAddress by lazy {
        InetAddress.getByName(MULTICAST_ADDRESS)
    }

    override fun setupSocket(): MulticastSocket {
        val multicastSocket = MulticastSocket(null)
        multicastSocket.reuseAddress = true
        multicastSocket.broadcast = true
        multicastSocket.loopbackMode = true

        try {
            multicastSocket.joinGroup(multicastAddress)
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
            multicastSocket.use {
                val messageId = uuid4()
                val requestMessage = OnvifCommands.probeCommand(messageId.toString()).toByteArray()
                val requestDatagram = DatagramPacket(requestMessage, requestMessage.size, multicastAddress, MULTICAST_PORT)

                repeat(retryCount) {
                    multicastSocket.send(requestDatagram)
                }

                while (currentCoroutineContext().isActive) {
                    val discoveryBuffer = ByteArray(MULTICAST_DATAGRAM_SIZE)
                    val discoveryDatagram = DatagramPacket(discoveryBuffer, discoveryBuffer.size)
                    it.receive(discoveryDatagram)
                    emit(discoveryDatagram)
                }
            }
        }.onCompletion { teardownSocket(multicastSocket) }
    }

    override fun teardownSocket(multicastSocket: MulticastSocket) {
        logger?.debug("Releasing resources")

        if (!multicastSocket.isClosed) {
            multicastSocket.leaveGroup(multicastAddress)
            multicastSocket.close()
        }
    }

    private companion object {
        const val MULTICAST_DATAGRAM_SIZE = 2048
        const val MULTICAST_PORT = 3702
        const val MULTICAST_ADDRESS = "239.255.255.250"
    }
}
