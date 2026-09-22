package com.seanproctor.onvifcamera.parsers

import com.seanproctor.onvifcamera.parseOnvifDeviceInformation
import com.seanproctor.onvifcamera.parseOnvifGetHostnameResponse
import com.seanproctor.onvifcamera.parseOnvifProfiles
import com.seanproctor.onvifcamera.parseOnvifSnapshotUri
import com.seanproctor.onvifcamera.parseOnvifStreamUri
import com.seanproctor.onvifcamera.readResourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ParserTest {
    @Test
    fun testStreamUriResponseParser() {
        val input = readResourceFile("./stream.xml")
        val result = parseOnvifStreamUri(input)
        assertEquals("rtsp://192.168.0.209/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast", result)
    }

    @Test
    fun testSnapshotUriResponseParser() {
        val input = readResourceFile("snapshot.xml")
        val result = parseOnvifSnapshotUri(input)
        assertEquals("http://192.168.0.209/onvif-cgi/jpg/image.cgi?resolution=1920x1080&compression=30", result)
    }

    @Test
    fun testProfilesResponseParser() {
        val input = readResourceFile("profiles.xml")
        val result = parseOnvifProfiles(input)
        assertEquals(2, result.size)
        assertEquals(listOf(1920 to 1080, 1920 to 1080), result.map { it.width to it.height })
    }

    @Test
    fun testProfilesResolutionDistinguishesSubStream() {
        val result = parseOnvifProfiles(readResourceFile("profiles2.xml"))
        val video = result.filter { it.encoding != null }
        assertEquals(listOf(2592 to 1944, 704 to 480), video.map { it.width to it.height })
    }

    @Test
    fun testProfiles2ResponseParser() {
        val input = readResourceFile("profiles2.xml")
        val result = parseOnvifProfiles(input)
        assertEquals(2, result.size)
        assertEquals("MediaProfile00000", result[0].token)
        assertEquals("MediaProfile_Channel1_MainStream", result[0].name)
        assertEquals("H264", result[0].encoding)
        assertEquals("MediaProfile00001", result[1].token)
        assertEquals("MediaProfile_Channel1_SubStream1", result[1].name)
        assertNotNull(result[1].encoding)
    }

    @Test
    fun testLorexProfilesResponseParser() {
        // The Lorex camera returns a profile whose video encoder configuration has no Encoding
        // element; the parser must tolerate the missing encoding rather than failing.
        val input = readResourceFile("lorex.xml")
        val result = parseOnvifProfiles(input)
        assertEquals(2, result.size)

        val profile000 = result.first { it.token == "Profile000" }
        assertNull(profile000.encoding)

        val profile001 = result.first { it.token == "Profile001" }
        assertEquals("H264", profile001.encoding)
    }

    @Test
    fun testDeviceInfoResponseParser() {
        val input = readResourceFile("deviceInfo.xml")
        val result = parseOnvifDeviceInformation(input)
        assertEquals("AXIS", result.manufacturer)
    }

    @Test
    fun testHostnameResponseParser() {
        val input = readResourceFile("hostname.xml")
        val result = parseOnvifGetHostnameResponse(input)
        assertEquals("CAMERA-01", result)
    }
}