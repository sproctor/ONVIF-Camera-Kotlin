package com.seanproctor.onvifcamera.conformance

import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.OnvifFault
import com.seanproctor.onvifcamera.OnvifLogger
import com.seanproctor.onvifcamera.network.OnvifDiscoveryManager
import io.ktor.http.Url
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The same expectations against a real camera. Skipped unless the environment names one:
 *
 * ```
 * ONVIF_CONFORMANCE_URL=http://192.168.4.48/onvif/device_service \
 * ONVIF_CONFORMANCE_USERNAME=admin ONVIF_CONFORMANCE_PASSWORD=secret \
 * ONVIF_CONFORMANCE_DISCOVERY=1 ./gradlew :onvifcamera:conformanceTest
 * ```
 *
 * `ONVIF_CONFORMANCE_DISCOVERY` additionally runs WS-Discovery on the local network and expects
 * at least one device to answer. It is separate because it needs a camera on the same network
 * as the machine running the suite, not merely reachable from it.
 */
class LiveConformanceTest {

    private val url = System.getenv("ONVIF_CONFORMANCE_URL")
    private val username = System.getenv("ONVIF_CONFORMANCE_USERNAME")
    private val password = System.getenv("ONVIF_CONFORMANCE_PASSWORD")

    /**
     * Counts HTTP exchanges from the library's own log so the run can say which authentication
     * the camera used: a WS-UsernameToken camera answers every request first time, an HTTP-layer
     * camera challenges each one with a 401 first.
     */
    private class Accounting : OnvifLogger {
        var requests = 0
        var challenges = 0
        var tokens = 0
        override fun debug(message: String) {
            if (message.startsWith("REQUEST:")) requests++
            if (message.startsWith("RESPONSE: 401")) challenges++
            if ("UsernameToken" in message) tokens++
        }
        override fun error(message: String, e: Throwable?) = Unit
    }

    @Test
    fun `LIVE - connect, read information, profiles, stream and snapshot URIs`() {
        assumeTrue("Set ONVIF_CONFORMANCE_URL (and USERNAME/PASSWORD) to run against a real camera", url != null)
        val accounting = Accounting()
        runBlocking {
            OnvifDevice.requestDevice(url!!, username, password, accounting).use { device ->
                val info = device.getDeviceInformation()
                println("LIVE device: ${info.manufacturer} ${info.model} firmware ${info.firmwareVersion}")
                assertTrue(info.manufacturer.isNotBlank(), "manufacturer")

                val profiles = device.getProfiles()
                println("LIVE profiles: " + profiles.joinToString { "${it.token} ${it.encoding} ${it.width}x${it.height}" })
                assertTrue(profiles.isNotEmpty(), "the camera reported no media profiles")
                val video = profiles.first { it.encoding != null }
                assertTrue(video.width != null && video.height != null, "video profile ${video.token} has no resolution")

                val stream = device.getStreamURI(video)
                println("LIVE stream: $stream")
                assertTrue(stream.startsWith("rtsp://", ignoreCase = true), "stream URI is not RTSP: $stream")
                assertEquals(Url(url).host, Url(stream).host, "stream URI host was not rewritten to the address used")

                val snapshot = try {
                    device.getSnapshotURI(video)
                } catch (e: OnvifFault) {
                    println("LIVE snapshot: not offered (${e.message})")
                    null
                }
                if (snapshot != null) {
                    println("LIVE snapshot: $snapshot")
                    assertTrue(snapshot.startsWith("http", ignoreCase = true), "snapshot URI is not HTTP: $snapshot")
                    assertEquals(Url(url).host, Url(snapshot).host, "snapshot URI host was not rewritten to the address used")
                    val jpeg = device.getSnapshot(snapshot)
                    println("LIVE snapshot image: ${jpeg.size} bytes")
                    assertTrue(
                        jpeg.size > 3 && jpeg[0] == 0xFF.toByte() && jpeg[1] == 0xD8.toByte(),
                        "snapshot body is not a JPEG (${jpeg.size} bytes)",
                    )
                }
            }
        }
        // Six operations: GetSystemDateAndTime, GetServices, then four. A camera that accepts the
        // WS-UsernameToken needs exactly six requests and no 401; one that authenticates at the
        // HTTP layer needs a 401 challenge before each authenticated operation.
        val mechanism = when {
            username == null -> "no credentials"
            accounting.challenges == 0 -> "WS-Security UsernameToken accepted, no HTTP challenge"
            else -> "HTTP challenge on ${accounting.challenges} of ${accounting.requests} requests (UsernameToken ignored or rejected)"
        }
        println("LIVE authentication: ${accounting.requests} HTTP requests, ${accounting.challenges} x 401, ${accounting.tokens} carried a UsernameToken: $mechanism")
    }

    @Test
    fun `LIVE - WS-Discovery finds at least one device on the local network`() {
        assumeTrue("Set ONVIF_CONFORMANCE_DISCOVERY=1 to run discovery", System.getenv("ONVIF_CONFORMANCE_DISCOVERY") != null)
        val devices = runBlocking {
            withTimeout(15_000) {
                OnvifDiscoveryManager().discoverDevices().first { it.isNotEmpty() }
            }
        }
        devices.forEach { println("LIVE discovered: ${it.id} at ${it.addresses}") }
        assertTrue(devices.all { it.addresses.isNotEmpty() }, "a device answered without any XAddrs")
    }
}
