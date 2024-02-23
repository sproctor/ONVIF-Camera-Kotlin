package com.seanproctor.onvifcamera.network

import android.net.wifi.WifiManager
import com.seanproctor.onvifcamera.OnvifCommands
import com.seanproctor.onvifcamera.OnvifLogger
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.isActive
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.util.*
import kotlin.io.use

/** Specific implementation of [SocketListener] */
internal class AndroidSocketListener(
    private val wifiManager: WifiManager,
    private val logger: OnvifLogger? = null,
) : SocketListener {

    private val multicastLock: WifiManager.MulticastLock by lazy {
        wifiManager.createMulticastLock("OnvifCamera")
    }

    private val multicastAddress: InetAddress by lazy {
        InetAddress.getByName(MULTICAST_ADDRESS)
    }

    override fun setupSocket(): MulticastSocket {
        multicastLock.setReferenceCounted(true)
        multicastLock.acquire()

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
                val messageId = UUID.randomUUID()
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

        if (multicastLock.isHeld) {
            multicastLock.release()
        }

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
