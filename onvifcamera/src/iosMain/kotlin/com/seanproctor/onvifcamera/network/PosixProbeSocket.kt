package com.seanproctor.onvifcamera.network

import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_ADDRESS
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_DATAGRAM_SIZE
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.MULTICAST_PORT
import com.seanproctor.onvifcamera.network.ProbeSocket.Companion.RECEIVE_TIMEOUT_MS
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.pointed
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.io.IOException
import platform.posix.AF_INET
import platform.posix.EAGAIN
import platform.posix.EINTR
import platform.posix.EWOULDBLOCK
import platform.posix.IFF_LOOPBACK
import platform.posix.IFF_MULTICAST
import platform.posix.IFF_UP
import platform.posix.INADDR_ANY
import platform.posix.INET_ADDRSTRLEN
import platform.posix.IPPROTO_IP
import platform.posix.IPPROTO_UDP
import platform.posix.IP_MULTICAST_IF
import platform.posix.IP_MULTICAST_LOOP
import platform.posix.SOCK_DGRAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.SO_REUSEADDR
import platform.posix.bind
import platform.posix.errno
import platform.darwin.freeifaddrs
import platform.darwin.getifaddrs
import platform.darwin.ifaddrs
import platform.posix.in_addr
import platform.darwin.inet_ntop
import platform.darwin.inet_pton
import platform.posix.recvfrom
import platform.posix.sendto
import platform.posix.setsockopt
import platform.posix.sockaddr
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.strerror
import platform.posix.timeval

/**
 * [ProbeSocket] on BSD sockets, for iOS. The same socket setup as the `java.net` one on the
 * JVM and Android: an ephemeral port, multicast loopback off, a read timeout, and a probe on
 * every IPv4 interface that can multicast.
 */
@OptIn(ExperimentalForeignApi::class)
internal class PosixProbeSocket(private val logger: OnvifLogger?) : ProbeSocket {

    // Listed before the socket is opened, so nothing after the open can fail but configure().
    private val interfaces: List<MulticastInterface> = multicastInterfaces()

    private val buffer = ByteArray(MULTICAST_DATAGRAM_SIZE)

    private val fd: Int = socket(AF_INET, SOCK_DGRAM, IPPROTO_UDP)

    init {
        if (fd < 0) throw posixError("socket")
        try {
            configure()
        } catch (e: Throwable) {
            platform.posix.close(fd)
            throw e
        }
    }

    private fun configure() = memScoped {
        val on = alloc<IntVar> { value = 1 }
        setOption(SOL_SOCKET, SO_REUSEADDR, on.ptr, sizeOf<IntVar>(), "SO_REUSEADDR")
        // Replies are unicast to this socket; our own probes looping back would only be noise.
        val loop = alloc<UByteVar> { value = 0u }
        setOption(IPPROTO_IP, IP_MULTICAST_LOOP, loop.ptr, sizeOf<UByteVar>(), "IP_MULTICAST_LOOP")
        // receive() blocks, and cancelling the collector cannot interrupt it; the timeout
        // bounds how long a cancelled run keeps the socket.
        val timeout = alloc<timeval> {
            tv_sec = (RECEIVE_TIMEOUT_MS / 1000).convert()
            tv_usec = (RECEIVE_TIMEOUT_MS % 1000 * 1000).convert()
        }
        setOption(SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>(), "SO_RCVTIMEO")
        // Probe matches are unicast back to the port the probe came from, so an ephemeral port
        // is enough, and joining the group is not needed: only replies to our probe are used.
        val local = alloc<sockaddr_in> {
            sin_len = sizeOf<sockaddr_in>().convert()
            sin_family = AF_INET.convert()
            sin_port = 0u
            sin_addr.s_addr = INADDR_ANY
        }
        if (bind(fd, local.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) throw posixError("bind")
        logger?.debug("Discovery socket bound")
    }

    override fun sendProbe(message: ByteArray): Unit = memScoped {
        val group = alloc<sockaddr_in> {
            sin_len = sizeOf<sockaddr_in>().convert()
            sin_family = AF_INET.convert()
            sin_port = MULTICAST_PORT.toNetworkOrder()
        }
        check(inet_pton(AF_INET, MULTICAST_ADDRESS, group.sin_addr.ptr) == 1)

        fun send(): Long = message.usePinned {
            sendto(fd, it.addressOf(0), message.size.convert(), 0, group.ptr.reinterpret<sockaddr>(), sizeOf<sockaddr_in>().convert())
        }

        if (interfaces.isEmpty()) {
            if (send() < 0) throw posixError("sendto")
            return
        }
        var sent = false
        var lastError: IOException? = null
        for (networkInterface in interfaces) {
            val address = alloc<in_addr> { s_addr = networkInterface.address }
            val result = if (setsockopt(fd, IPPROTO_IP, IP_MULTICAST_IF, address.ptr, sizeOf<in_addr>().convert()) != 0) -1 else send()
            if (result >= 0) {
                sent = true
            } else {
                val error = posixError("probe on ${networkInterface.name}")
                logger?.debug("Could not probe on ${networkInterface.name}: ${error.message}")
                lastError = error
            }
        }
        if (!sent) throw checkNotNull(lastError)
    }

    override fun receive(): Datagram? = memScoped {
        val sender = alloc<sockaddr_in>()
        val senderLength = alloc<socklen_tVar> { value = sizeOf<sockaddr_in>().convert() }
        val length = buffer.usePinned {
            recvfrom(fd, it.addressOf(0), buffer.size.convert(), 0, sender.ptr.reinterpret(), senderLength.ptr)
        }
        if (length < 0) {
            val code = errno
            // EAGAIN is the read timeout; EINTR a signal. Either way the caller loops.
            if (code == EAGAIN || code == EWOULDBLOCK || code == EINTR) return null
            throw posixError("recvfrom", code)
        }
        val host = allocArray<ByteVar>(INET_ADDRSTRLEN)
        inet_ntop(AF_INET, sender.sin_addr.ptr, host, INET_ADDRSTRLEN.convert()) ?: throw posixError("inet_ntop")
        Datagram(buffer.copyOf(length.toInt()), host.toKString())
    }

    override fun close() {
        logger?.debug("Releasing resources")
        platform.posix.close(fd)
    }

    private fun setOption(level: Int, option: Int, value: CValuesRef<*>, size: Long, name: String) {
        if (setsockopt(fd, level, option, value, size.convert()) != 0) throw posixError("setsockopt $name")
    }

    /** Up, not loopback, multicast-capable interfaces with an IPv4 address: one entry each. */
    private fun multicastInterfaces(): List<MulticastInterface> = memScoped {
        val list = alloc<CPointerVar<ifaddrs>>()
        if (getifaddrs(list.ptr) != 0) {
            logger?.error("Could not list network interfaces", posixError("getifaddrs"))
            return emptyList()
        }
        try {
            val found = LinkedHashMap<String, MulticastInterface>()
            var entry: CPointer<ifaddrs>? = list.value
            while (entry != null) {
                val ifa = entry.pointed
                val flags = ifa.ifa_flags.toInt()
                val address = ifa.ifa_addr
                val name = ifa.ifa_name?.toKString()
                if (name != null && address != null && address.pointed.sa_family.toInt() == AF_INET &&
                    flags and IFF_UP != 0 && flags and IFF_LOOPBACK == 0 && flags and IFF_MULTICAST != 0
                ) {
                    found.getOrPut(name) {
                        MulticastInterface(name, address.reinterpret<sockaddr_in>().pointed.sin_addr.s_addr)
                    }
                }
                entry = ifa.ifa_next
            }
            found.values.toList()
        } finally {
            freeifaddrs(list.value)
        }
    }

    /** An interface's name and one of its IPv4 addresses, in network byte order. */
    private class MulticastInterface(val name: String, val address: UInt)
}

/** Apple platforms are little-endian; a port goes on the wire big-endian. */
private fun Int.toNetworkOrder(): UShort = (((this and 0xFF) shl 8) or ((this shr 8) and 0xFF)).toUShort()

@OptIn(ExperimentalForeignApi::class)
private fun posixError(call: String, code: Int = errno): IOException =
    IOException("$call failed: ${strerror(code)?.toKString() ?: "errno $code"}")
