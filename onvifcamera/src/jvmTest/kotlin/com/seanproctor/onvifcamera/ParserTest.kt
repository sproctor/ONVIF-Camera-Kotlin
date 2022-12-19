package com.seanproctor.onvifcamera

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ParserTest {
    @Test
    fun testProbeResponseParser() {
        val input = readResourceFile("probeResponse.xml")
        val result = parseOnvifProbeResponse(input.decodeToString())
        assertEquals(1, result.size)
    }

    @Test
    fun testServicesResponseParser() {
        val input = readResourceFile("services.xml")
        val result = parseOnvifServices(input.decodeToString())
        val namespace = result["http://www.onvif.org/ver10/search/wsdl"]
        assertNotNull(namespace)
        assertContains(namespace, "/onvif/services")
    }

    @Test
    fun testStreamUriResponseParser() {
        val input = readResourceFile("stream.xml")
        val result = parseOnvifStreamUri(input.decodeToString())
        assertEquals("rtsp://192.168.0.209/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast", result)
    }

    @Test
    fun testSnapshotUriResponseParser() {
        val input = readResourceFile("snapshot.xml")
        val result = parseOnvifSnapshotUri(input.decodeToString())
        assertEquals("http://192.168.0.209/onvif-cgi/jpg/image.cgi?resolution=1920x1080&compression=30", result)
    }

    @Test
    fun testProfilesResponseParser() {
        val input = readResourceFile("profiles.xml")
        val result = parseOnvifProfiles(input.decodeToString())
        assertEquals(2, result.size)
    }

    @Test
    fun testDeviceInfoResponseParser() {
        val input = readResourceFile("deviceInfo.xml")
        val result = parseOnvifDeviceInformation(input.decodeToString())
        assertEquals("AXIS", result.manufacturer)
    }
}