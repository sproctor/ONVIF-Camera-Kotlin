package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifCommands
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.uuid.Uuid

/**
 * A blocking UDP socket on an ephemeral port, set up for WS-Discovery: probes go to the
 * multicast group, and probe matches come back to the socket's own port by unicast.
 */
internal interface ProbeSocket : AutoCloseable {
    /**
     * Sends [message] to the WS-Discovery group on every interface that can multicast. One
     * interface refusing it is routine (a VPN tunnel, a bridge with no route), but if it went out
     * on none this throws: listening for replies that cannot come would look exactly like an
     * empty network.
     */
    fun sendProbe(message: ByteArray)

    /** The next datagram, or null if none arrived within [RECEIVE_TIMEOUT_MS]. */
    fun receive(): Datagram?

    companion object {
        const val MULTICAST_ADDRESS = "239.255.255.250"
        const val MULTICAST_PORT = 3702
        const val MULTICAST_DATAGRAM_SIZE = 64 * 1024
        const val RECEIVE_TIMEOUT_MS = 500
    }
}

/** The [SocketListener] every platform uses; [openSocket] supplies the platform's socket. */
internal class ProbingSocketListener(
    private val openSocket: () -> ProbeSocket,
) : SocketListener {

    override fun listenForPackets(): Flow<Datagram> = flow {
        openSocket().use { socket ->
            coroutineScope {
                // Probes are spaced out (see sendProbes), so they go from a child coroutine
                // while this one receives; replies to the first probe are not held up by the
                // schedule. A send failure cancels the scope and fails the flow.
                launch { sendProbes(socket) }

                // receive() blocks and cancellation cannot interrupt it, so the socket has a
                // read timeout: the loop re-checks isActive at least every RECEIVE_TIMEOUT_MS,
                // which bounds how long a cancelled collector's socket stays open.
                while (isActive) {
                    socket.receive()?.let { emit(it) }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Sends the probe on the SOAP-over-UDP 1.1 multicast schedule: MULTICAST_UDP_REPEAT
     * retransmissions after the first send, spaced by a random 50–250 ms gap that doubles per
     * repeat and caps at 500 ms. Every repeat is the same message, MessageID included, which is
     * what lets a receiver discard the duplicates; a repeat only matters when the original was
     * lost, and one sent back-to-back would be lost to the same burst. The jitter keeps several
     * clients on one network from retransmitting in lockstep.
     */
    private suspend fun sendProbes(socket: ProbeSocket) {
        val request = OnvifCommands.probeCommand(Uuid.random().toString()).encodeToByteArray()
        var gapMs = Random.nextLong(UDP_MIN_DELAY_MS, UDP_MAX_DELAY_MS + 1)
        repeat(1 + MULTICAST_UDP_REPEAT) { attempt ->
            if (attempt > 0) {
                delay(gapMs)
                gapMs = (gapMs * 2).coerceAtMost(UDP_UPPER_DELAY_MS)
            }
            socket.sendProbe(request)
        }
    }

    private companion object {
        // SOAP-over-UDP 1.1 retransmission constants.
        const val MULTICAST_UDP_REPEAT = 2
        const val UDP_MIN_DELAY_MS = 50L
        const val UDP_MAX_DELAY_MS = 250L
        const val UDP_UPPER_DELAY_MS = 500L
    }
}
