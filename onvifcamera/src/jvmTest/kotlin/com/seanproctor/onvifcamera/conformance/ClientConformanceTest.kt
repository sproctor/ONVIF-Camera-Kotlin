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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Client conformance against a [FakeOnvifDevice]. Each test is one behaviour the ONVIF Client
 * Test Specification (Profile S/T client, Device Management and Media/Media2 sections) expects
 * of a client that uses the operations this library uses, and each ends by asserting the fake
 * device saw no specification violation in what the library sent.
 *
 * Not part of `check`: run with `./gradlew :onvifcamera:conformanceTest`.
 */
class ClientConformanceTest {

    private val user = "admin"
    private val pass = "secret"

    private fun assertConformant(fake: FakeOnvifDevice) {
        assertTrue(fake.violations.isEmpty(), "Specification violations:\n  " + fake.violations.joinToString("\n  "))
    }

    private fun connect(fake: FakeOnvifDevice, password: String = pass): OnvifDevice =
        runBlocking { OnvifDevice.requestDevice(fake.deviceServiceUrl, user, password) }

    // ---- Device management --------------------------------------------------------------------

    @Test
    fun `DEVICE - connects with SOAP 1_2 over HTTP, Digest authentication, and GetServices`() {
        FakeOnvifDevice().use { fake ->
            connect(fake)
            assertEquals(listOf("device_service GetServices"), fake.operations)
            assertConformant(fake)
        }
    }

    @Test
    fun `DEVICE - GetDeviceInformation is read`() {
        FakeOnvifDevice().use { fake ->
            val info = runBlocking { connect(fake).getDeviceInformation() }
            assertEquals("Conformance", info.manufacturer)
            assertEquals("FakeCam", info.model)
            assertEquals("FAKE0001", info.serialNumber)
            assertConformant(fake)
        }
    }

    @Test
    fun `DEVICE - wrong credentials surface as OnvifUnauthorized and are never retried in clear text`() {
        FakeOnvifDevice().use { fake ->
            assertFailsWith<OnvifUnauthorized> { connect(fake, password = "wrong") }
            // The fake records a violation if Basic is sent after its Digest challenge.
            assertConformant(fake)
        }
    }

    @Test
    fun `DEVICE - a device that challenges with Basic is answered with Basic`() {
        FakeOnvifDevice(challengeScheme = "Basic").use { fake ->
            val info = runBlocking { connect(fake).getDeviceInformation() }
            assertEquals("FakeCam", info.model)
            assertConformant(fake)
        }
    }

    @Test
    fun `DEVICE - a device that needs no authentication is not sent any`() {
        FakeOnvifDevice(credentials = null).use { fake ->
            val info = runBlocking { OnvifDevice.requestDevice(fake.deviceServiceUrl).getDeviceInformation() }
            assertEquals("FakeCam", info.model)
            assertConformant(fake)
        }
    }

    // ---- Media2 -------------------------------------------------------------------------------

    @Test
    fun `MEDIA2 - GetProfiles asks for the VideoEncoder configuration and reads it`() {
        FakeOnvifDevice().use { fake ->
            val profiles = runBlocking { connect(fake).getProfiles() }
            assertEquals(listOf(MAIN_PROFILE, SUB_PROFILE), profiles.map { it.token })
            assertEquals("H265", profiles[0].encoding)
            assertEquals(2688 to 1520, profiles[0].width to profiles[0].height)
            assertEquals("H264", profiles[1].encoding)
            assertEquals(listOf("device_service GetServices", "media2_service GetProfiles"), fake.operations)
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA2 - GetStreamUri names a tr2 TransportProtocol and the URI host is rewritten to the address used`() {
        FakeOnvifDevice().use { fake ->
            val device = connect(fake)
            val uri = runBlocking { device.getStreamURI(device.getProfiles()[0]) }
            assertEquals("rtsp://${fake.host}:554/cam/realmonitor?channel=1&subtype=0&unicast=true&proto=Onvif", uri)
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA2 - GetSnapshotUri is read and its host rewritten`() {
        FakeOnvifDevice().use { fake ->
            val device = connect(fake)
            val uri = runBlocking { device.getSnapshotURI(device.getProfiles()[0]) }
            assertEquals("http://${fake.host}/onvifsnapshot/media_service/snapshot?channel=1&subtype=0", uri)
            assertConformant(fake)
        }
    }

    // ---- Media1 fallback ------------------------------------------------------------------------

    @Test
    fun `MEDIA1 - used for every media call when Media2 is not offered`() {
        FakeOnvifDevice(media = setOf(MediaService.MEDIA1)).use { fake ->
            val device = connect(fake)
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

            assertTrue(fake.operations.none { it.startsWith("media2_service") }, fake.operations.toString())
            assertEquals(
                listOf("media_service GetProfiles", "media_service GetStreamUri", "media_service GetSnapshotUri"),
                fake.operations.drop(1),
            )
            assertConformant(fake)
        }
    }

    @Test
    fun `MEDIA - a device offering neither media service fails the media calls without a request`() {
        FakeOnvifDevice(media = emptySet()).use { fake ->
            val device = connect(fake)
            val profile = MediaProfile(token = MAIN_PROFILE, name = null, encoding = "H264")
            val e = assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getProfiles() } }
            assertTrue(MEDIA1_NS in e.message.orEmpty() && MEDIA2_NS in e.message.orEmpty(), e.message)
            assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getStreamURI(profile) } }
            assertFailsWith<OnvifServiceUnavailable> { runBlocking { device.getSnapshotURI(profile) } }
            assertEquals(listOf("device_service GetServices"), fake.operations)
            assertConformant(fake)
        }
    }

    // ---- Faults and errors ----------------------------------------------------------------------

    @Test
    fun `FAULT - a SOAP fault is reported as OnvifFault with its codes, whether sent with 400 or 200`() {
        for (status in listOf(400, 500, 200)) {
            FakeOnvifDevice(faultStatus = status).use { fake ->
                val device = connect(fake)
                val profiles = runBlocking { device.getProfiles() }
                val e = assertFailsWith<OnvifFault>("fault with HTTP $status") { runBlocking { device.getSnapshotURI(profiles[1]) } }
                assertEquals("Receiver", e.code)
                assertEquals(listOf("ActionNotSupported"), e.subcodes)
                assertEquals("Snapshot is not supported for this profile", e.reason)
                assertConformant(fake)
            }
        }
    }

    @Test
    fun `FAULT - an unknown profile token is a Sender InvalidArgVal NoProfile fault`() {
        FakeOnvifDevice().use { fake ->
            val device = connect(fake)
            val e = assertFailsWith<OnvifFault> {
                runBlocking { device.getStreamURI(MediaProfile(token = "no-such-profile", name = null, encoding = "H264")) }
            }
            assertEquals("Sender", e.code)
            assertEquals(listOf("InvalidArgVal", "NoProfile"), e.subcodes)
            assertConformant(fake)
        }
    }

    @Test
    fun `FAULT - a NotAuthorized fault, even with HTTP 200, is OnvifUnauthorized`() {
        FakeOnvifDevice(notAuthorizedAsFault = true).use { fake ->
            val device = connect(fake)
            assertFailsWith<OnvifUnauthorized> { runBlocking { device.getProfiles() } }
            assertConformant(fake)
        }
    }

    @Test
    fun `ERROR - HTTP 403 is OnvifForbidden`() {
        FakeOnvifDevice(forbidden = setOf("GetDeviceInformation")).use { fake ->
            val device = connect(fake)
            assertFailsWith<OnvifForbidden> { runBlocking { device.getDeviceInformation() } }
            assertConformant(fake)
        }
    }

    @Test
    fun `ERROR - a 200 that is not the expected reply is OnvifInvalidResponse`() {
        FakeOnvifDevice(htmlPageFor = setOf("GetDeviceInformation")).use { fake ->
            val device = connect(fake)
            assertFailsWith<OnvifInvalidResponse> { runBlocking { device.getDeviceInformation() } }
            assertConformant(fake)
        }
    }
}
