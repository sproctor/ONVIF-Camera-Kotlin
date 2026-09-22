package com.seanproctor.onvifcamera

import io.ktor.client.HttpClient
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MediaServiceTest {

    private val device = "http://www.onvif.org/ver10/device/wsdl"

    @Test
    fun media2IsPreferredWhenBothAreOffered() {
        assertEquals(
            MediaService.MEDIA2,
            MediaService.offeredBy(listOf(device, MediaService.MEDIA1.namespace, MediaService.MEDIA2.namespace)),
        )
    }

    @Test
    fun media1IsTheFallback() {
        assertEquals(MediaService.MEDIA1, MediaService.offeredBy(listOf(device, MediaService.MEDIA1.namespace)))
    }

    @Test
    fun neitherIsNull() {
        assertNull(MediaService.offeredBy(listOf(device)))
        assertNull(MediaService.offeredBy(emptyList()))
    }

    @Test
    fun aDeviceWithNoMediaServiceFailsEveryMediaCallBeforeTouchingTheNetwork() = runTest {
        // Only the device service is advertised. Nothing is sent: the failure is decided from the
        // services list, so an unreachable address is fine here.
        OnvifDevice(
            address = Url("http://192.0.2.1"),
            credentials = null,
            namespaceMap = mapOf(device to "/onvif/device_service"),
            clockOffset = null,
            client = HttpClient(),
            logger = null,
        ).use { camera ->
            val profile = MediaProfile(token = "Profile_1", name = null, encoding = "H264")

            val e = assertFailsWith<OnvifServiceUnavailable> { camera.getProfiles() }
            assertEquals(MediaService.MEDIA2.namespace, e.namespace)
            assertContains(e.message.orEmpty(), MediaService.MEDIA1.namespace)
            assertFailsWith<OnvifServiceUnavailable> { camera.getStreamURI(profile) }
            assertFailsWith<OnvifServiceUnavailable> { camera.getSnapshotURI(profile) }
        }
    }
}
