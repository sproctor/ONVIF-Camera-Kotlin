package com.seanproctor.onvifcamera.network

import android.net.wifi.WifiManager
import android.util.Log
import com.benasher44.uuid.uuid4
import com.seanproctor.onvifcamera.OnvifCommands
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

/** Specific implementation of [SocketListener] */
internal class AndroidSocketListener(
    private val wifiManager: WifiManager,
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
            Log.d(TAG, "MulticastSocket has been setup")
        } catch (ex: Exception) {
            Log.e(TAG, "Could finish setting up the multicast socket and group", ex)
        }

        return multicastSocket
    }

    override fun listenForPackets(retryCount: Int): Flow<DatagramPacket> {
        Log.d(TAG, "Setting up datagram packet flow")
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
        Log.d(TAG, "Releasing resources")

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
        const val TAG = "AndroidSocketListener"
    }
}
