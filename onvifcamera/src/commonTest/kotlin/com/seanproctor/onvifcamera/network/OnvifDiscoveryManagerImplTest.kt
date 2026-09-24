package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.readResourceFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Drives [OnvifDiscoveryManagerImpl] with a scripted [SocketListener] to pin the flow's
 * contract: what it emits, in what order, and how it fails.
 */
class OnvifDiscoveryManagerImplTest {

    private val axisReply = readResourceFile("probeResponse.xml")
    private val otherReply = readResourceFile("probeResponse2.xml")

    private fun packet(body: String, from: String) = Datagram(body.encodeToByteArray(), from)

    private fun manager(vararg packets: Datagram) = manager(flowOf(*packets))

    private fun manager(packets: Flow<Datagram>) = OnvifDiscoveryManagerImpl(
        socketListener = object : SocketListener {
            override fun listenForPackets() = packets
        },
        logger = null,
    )

    @Test
    fun startsEmptyThenAccumulatesInArrivalOrder() = runTest {
        val states = manager(
            packet(axisReply, "192.168.0.209"),
            packet(otherReply, "192.168.0.210"),
        ).discoverDevices().toList()

        assertEquals(listOf(0, 1, 2), states.map { it.size })
        // The first device keeps its position when the second arrives.
        assertEquals(states[1][0], states[2][0])
    }

    @Test
    fun identicalRepliesToRetransmissionsAreConflated() = runTest {
        val states = manager(
            packet(axisReply, "192.168.0.209"),
            packet(axisReply, "192.168.0.209"),
            packet(axisReply, "192.168.0.209"),
        ).discoverDevices().toList()

        assertEquals(listOf(0, 1), states.map { it.size })
    }

    @Test
    fun devicesAreKeyedBySenderAddress() = runTest {
        val states = manager(
            packet(axisReply, "192.168.0.209"),
            packet(axisReply, "192.168.0.211"),
        ).discoverDevices().toList()

        assertEquals(2, states.last().size)
    }

    @Test
    fun unparseableDatagramsAreDropped() = runTest {
        val states = manager(
            packet("this is not a probe match", "192.168.0.1"),
            packet(axisReply, "192.168.0.209"),
        ).discoverDevices().toList()

        assertEquals(listOf(0, 1), states.map { it.size })
    }

    @Test
    fun socketFailureFailsTheFlow() = runTest {
        val failing = flow<Datagram> { throw IOException("no network") }

        assertFailsWith<IOException> {
            manager(failing).discoverDevices().toList()
        }
    }
}
