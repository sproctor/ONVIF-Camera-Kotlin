package com.seanproctor.onvifcamera.conformance

import com.seanproctor.onvifcamera.MediaProfile
import com.seanproctor.onvifcamera.MediaService
import com.seanproctor.onvifcamera.OnvifDevice
import com.seanproctor.onvifcamera.OnvifFault
import com.seanproctor.onvifcamera.OnvifForbidden
import com.seanproctor.onvifcamera.OnvifInvalidResponse
import com.seanproctor.onvifcamera.OnvifServiceUnavailable
import com.seanproctor.onvifcamera.OnvifUnauthorized
import kotlinx.coroutines.runBlocking
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Client conformance against a [FakeOnvifDevice]. Each test is one behaviour the ONVIF Client
 * Test Specification (Profile S/T client: Device Management, Security, Media/Media2) expects of
 * a client that uses the operations this library uses, and each ends by asserting the fake
 * device saw no specification violation in what the library sent.
 *
 * Not part of `check`: run with `./gradlew :onvifcamera:conformanceTest`.
 */
class ClientConformanceTest {

    private val user = "admin"
    private val pass = "secret"

    private val connectOperations = listOf("device_service GetSystemDateAndTime", "device_service GetServices")

    private fun assertConformant(fake: FakeOnvifDevice) {
        assertTrue(fake.violations.isEmpty(), "Specification violations:\n  " + fake.violations.joinToString("\n  "))
    }

    private fun connect(fake: FakeOnvifDevice, password: String = pass): OnvifDevice =
        runBlocking { OnvifDevice.requestDevice(fake.deviceServiceUrl, user, password) }

    private inline fun <T> FakeOnvifDevice.withDevice(password: String = pass, block: (OnvifDevice) -> T): T =
        connect(this, password).use(block)

    // ---- Device management and security ------------------------------------------------------

    @Test
    fun `SECURITY - connecting reads the device clock unauthenticated, then GetServices with a UsernameToken, no 401s`() {
        FakeOnvifDevice().use { fake ->
            fake.withDevice { }
            assertEquals(connectOperations, fake.operations)
            assertEquals(0, fake.challenges.get(), "a WS-Security client should never be challenged")
            assertEquals(1, fake.usernameTokens.get(), "GetServices carries the token, GetSystemDateAndTime must not")
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - every authenticated operation is one round trip`() {
        FakeOnvifDevice().use { fake ->
            fake.withDevice { device ->
                runBlocking {
                    device.getDeviceInformation()
                    val profiles = device.getProfiles()
                    device.getStreamURI(profiles[0])
                }
            }
            assertEquals(0, fake.challenges.get())
            assertEquals(4, fake.usernameTokens.get(), "GetServices plus three operations, one token each")
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - a device that authenticates only in SOAP works without any HTTP challenge`() {
        FakeOnvifDevice(wsSecurityOnly = true).use { fake ->
            val info = fake.withDevice { device -> runBlocking { device.getDeviceInformation() } }
            assertEquals("FakeCam", info.model)
            assertEquals(0, fake.challenges.get())
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - a SOAP-only device rejecting the credentials surfaces as OnvifUnauthorized`() {
        FakeOnvifDevice(wsSecurityOnly = true).use { fake ->
            fake.withDevice(password = "wrong") { device ->
                assertFailsWith<OnvifUnauthorized> { runBlocking { device.getDeviceInformation() } }
            }
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - token timestamps follow the device clock, not this host's`() {
        for (skew in listOf(Duration.ofMinutes(10), Duration.ofMinutes(-10), Duration.ofHours(3))) {
            FakeOnvifDevice(wsSecurityOnly = true, clockSkew = skew).use { fake ->
                val info = fake.withDevice { device -> runBlocking { device.getDeviceInformation() } }
                assertEquals("FakeCam", info.model, "device clock $skew off")
                assertConformant(fake)
            }
        }
    }

    @Test
    fun `SECURITY - a device that ignores WS-Security and challenges with Digest is answered with Digest`() {
        FakeOnvifDevice(ignoreUsernameToken = true).use { fake ->
            val info = fake.withDevice { device -> runBlocking { device.getDeviceInformation() } }
            assertEquals("FakeCam", info.model)
            assertTrue(fake.challenges.get() >= 1, "the HTTP fallback should have been exercised")
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - a device that challenges with Basic is answered with Basic`() {
        FakeOnvifDevice(ignoreUsernameToken = true, challengeScheme = "Basic").use { fake ->
            val info = fake.withDevice { device -> runBlocking { device.getDeviceInformation() } }
            assertEquals("FakeCam", info.model)
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - wrong credentials surface as OnvifUnauthorized and are never retried in clear text`() {
        FakeOnvifDevice().use { fake ->
            fake.withDevice(password = "wrong") { device ->
                assertFailsWith<OnvifUnauthorized> { runBlocking { device.getDeviceInformation() } }
            }
            // The fake records a violation if Basic is sent after its Digest challenge.
            assertConformant(fake)
        }
    }

    @Test
    fun `SECURITY - a device that needs no authentication is sent none`() {
        FakeOnvifDevice(credentials = null).use { fake ->
            val info = runBlocking {
                OnvifDevice.requestDevice(fake.deviceServiceUrl).use { it.getDeviceInformation() }
            }
            assertEquals("FakeCam", info.model)
            assertEquals(0, fake.usernameTokens.get())
            assertConformant(fake)
        }
    }

    @Test
    fun `DEVICE - GetDeviceInformation is read`() {
        FakeOnvifDevice().use { fake ->
            val info = fake.withDevice { device -> runBlocking { device.getDeviceInformation() } }
            assertEquals("Conformance", info.manufacturer)
            assertEquals("FakeCam", info.model)
            assertEquals("FAKE0001", info.serialNumber)
            assertConformant(fake)
        }
    }

    // ---- Media2 -------------------------------------------------------------------------------

    @Test
    fun `MEDIA2 - GetProfiles asks for the VideoEncoder configuration and reads it`() {
        FakeOnvifDevice().use { fake ->
            val profiles = fake.withDevice { device -> runBlocking { device.getProfiles() } }
            assertEquals(listOf(MAIN_PROFILE, SUB_PROFILE), profiles.map { it.token })
            assertEquals("H265", profiles[0].encoding)
            assertEquals(2688 to 1520, profiles[0].width to profiles[0].height)
            assertEquals("H264", profiles[1].encoding)
            assertEquals(connectOperations + "media2_service GetProfiles", fake.operations)
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA2 - GetStreamUri names a tr2 TransportProtocol and the URI host is rewritten to the address used`() {
        FakeOnvifDevice().use { fake ->
            val uri = fake.withDevice { device -> runBlocking { device.getStreamURI(device.getProfiles()[0]) } }
            assertEquals("rtsp://${fake.host}:554/cam/realmonitor?channel=1&subtype=0&unicast=true&proto=Onvif", uri)
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA2 - GetSnapshotUri is read and its host rewritten`() {
        FakeOnvifDevice().use { fake ->
            val uri = fake.withDevice { device -> runBlocking { device.getSnapshotURI(device.getProfiles()[0]) } }
            assertEquals("http://${fake.host}/onvifsnapshot/media_service/snapshot?channel=1&subtype=0", uri)
            assertConformant(fake)
        }
    }

    // ---- Media1 fallback ------------------------------------------------------------------------

    @Test
    fun `MEDIA1 - used for every media call when Media2 is not offered`() {
        FakeOnvifDevice(media = setOf(MediaService.MEDIA1)).use { fake ->
            fake.withDevice { device ->
                val profiles = runBlocking { device.getProfiles() }
                assertEquals(listOf(MAIN_PROFILE, SUB_PROFILE), profiles.map { it.token })
                // Media1 cannot say H265, so the main profile has no encoding; the sub stream is intact.
                assertEquals(null, profiles[0].encoding)
                assertEquals("H264", profiles[1].encoding)
                assertEquals(704 to 480, profiles[1].width to profiles[1].height)

                val stream = runBlocking { device.getStreamURI(profiles[1]) }
                assertEquals("rtsp://${fake.host}:554/cam/realmonitor?channel=1&subtype=1&unicast=true&proto=Onvif", stream)
                val snapshot = runBlocking { device.getSnapshotURI(profiles[0]) }
                assertEquals("http://${fake.host}/onvifsnapshot/media_service/snapshot?channel=1&subtype=0", snapshot)
            }
            assertTrue(fake.operations.none { it.startsWith("media2_service") }, fake.operations.toString())
            assertEquals(
                listOf("media_service GetProfiles", "media_service GetStreamUri", "media_service GetSnapshotUri"),
                fake.operations.drop(connectOperations.size),
            )
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA - a device offering neither media service fails the media calls without a request`() {
        FakeOnvifDevice(media = emptySet()).use { fake ->
            fake.withDevice { device ->
                val profile = MediaProfile(token = MAIN_PROFILE, name = null, encoding = "H264")
                val e = assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getProfiles() } }
                assertTrue(MEDIA1_NS in e.message.orEmpty() && MEDIA2_NS in e.message.orEmpty(), e.message)
                assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getStreamURI(profile) } }
                assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getSnapshotURI(profile) } }
            }
            assertEquals(connectOperations, fake.operations)
            assertConformant(fake)
        }
    }

    // ---- Faults and errors ----------------------------------------------------------------------

    @Test
    fun `FAULT - a SOAP fault is reported as OnvifFault with its codes, whether sent with 400 or 200`() {
        for (status in listOf(400, 500, 200)) {
            FakeOnvifDevice(faultStatus = status).use { fake ->
                fake.withDevice { device ->
                    val profiles = runBlocking { device.getProfiles() }
                    val e = assertFailsWith<OnvifFault>("fault with HTTP $status") {
                        runBlocking { device.getSnapshotURI(profiles[1]) }
                    }
                    assertEquals("Receiver", e.code)
                    assertEquals(listOf("ActionNotSupported"), e.subcodes)
                    assertEquals("Snapshot is not supported for this profile", e.reason)
                }
                assertConformant(fake)
            }
        }
    }

    @Test
    fun `FAULT - an unknown profile token is a Sender InvalidArgVal NoProfile fault`() {
        FakeOnvifDevice().use { fake ->
            fake.withDevice { device ->
                val e = assertFailsWith<OnvifFault> {
                    runBlocking { device.getStreamURI(MediaProfile(token = "no-such-profile", name = null, encoding = "H264")) }
                }
                assertEquals("Sender", e.code)
                assertEquals(listOf("InvalidArgVal", "NoProfile"), e.subcodes)
            }
            assertConformant(fake)
        }
    }

    @Test
    fun `FAULT - a NotAuthorized fault, even with HTTP 200, is OnvifUnauthorized`() {
        FakeOnvifDevice(notAuthorizedAsFault = true).use { fake ->
            fake.withDevice { device ->
                assertFailsWith<OnvifUnauthorized> { runBlocking { device.getProfiles() } }
            }
            assertConformant(fake)
        }
    }

    @Test
    fun `ERROR - HTTP 403 is OnvifForbidden`() {
        FakeOnvifDevice(forbidden = setOf("GetDeviceInformation")).use { fake ->
            fake.withDevice { device ->
                assertFailsWith<OnvifForbidden> { runBlocking { device.getDeviceInformation() } }
            }
            assertConformant(fake)
        }
    }

    @Test
    fun `ERROR - a 200 that is not the expected reply is OnvifInvalidResponse`() {
        FakeOnvifDevice(htmlPageFor = setOf("GetDeviceInformation")).use { fake ->
            fake.withDevice { device ->
                assertFailsWith<OnvifInvalidResponse> { runBlocking { device.getDeviceInformation() } }
            }
            assertConformant(fake)
        }
    }
}
