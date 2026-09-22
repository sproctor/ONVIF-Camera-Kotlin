package com.seanproctor.onvifcamera.parsers

import com.seanproctor.onvifcamera.MediaService
import com.seanproctor.onvifcamera.parseOnvifProfiles
import com.seanproctor.onvifcamera.parseOnvifProfilesMedia1
import com.seanproctor.onvifcamera.parseOnvifSnapshotUri
import com.seanproctor.onvifcamera.parseOnvifSnapshotUriMedia1
import com.seanproctor.onvifcamera.parseOnvifStreamUri
import com.seanproctor.onvifcamera.parseOnvifStreamUriMedia1
import com.seanproctor.onvifcamera.readResourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Media1 (trt) responses, used when a device offers no Media2. The profile fixtures are real
 * captures; the URI fixtures are derived from the Media2 captures, see their comments.
 */
class Media1ParserTest {

    @Test
    fun axisProfilesInlineTheEncoder() {
        val result = parseOnvifProfilesMedia1(readResourceFile("profiles_media1.xml"))
        assertEquals(listOf("profile_1_h264", "profile_1_jpeg"), result.map { it.token })
        assertEquals(listOf("H264", "JPEG"), result.map { it.encoding })
        assertEquals(listOf(1920 to 1080, 1920 to 1080), result.map { it.width to it.height })
    }

    @Test
    fun mainAndSubStreamKeepTheirResolutions() {
        val result = parseOnvifProfilesMedia1(readResourceFile("profiles2_media1.xml"))
        assertEquals(listOf("MediaProfile00000", "MediaProfile00001"), result.map { it.token })
        assertEquals(listOf(2592 to 1944, 704 to 480), result.map { it.width to it.height })
    }

    @Test
    fun lorexLeavesEncodingOutOfItsH265Profile() {
        // Media1's VideoEncoding enumeration has no H265, so the camera omits Encoding (and the
        // resolution) from its main-stream profile. Through Media2 the same profile reports
        // H265 2688x1520. The parser must tolerate the gap rather than fail the whole list.
        val result = parseOnvifProfilesMedia1(readResourceFile("lorex_media1.xml"))
        assertEquals(2, result.size)
        val main = result.first { it.token == "Profile000" }
        assertNull(main.encoding)
        assertNull(main.width)
        assertEquals("H264", result.first { it.token == "Profile001" }.encoding)
    }

    @Test
    fun streamUriIsUnwrappedFromMediaUri() {
        assertEquals(
            "rtsp://192.168.0.209/onvif-media/media.amp?profile=profile_1_h264&sessiontimeout=60&streamtype=unicast",
            parseOnvifStreamUriMedia1(readResourceFile("stream_media1.xml")),
        )
    }

    @Test
    fun snapshotUriIsUnwrappedFromMediaUri() {
        assertEquals(
            "http://192.168.0.209/onvif-cgi/jpg/image.cgi?resolution=1920x1080&compression=30",
            parseOnvifSnapshotUriMedia1(readResourceFile("snapshot_media1.xml")),
        )
    }

    @Test
    fun dispatchersPickTheParserForTheService() {
        assertEquals(
            parseOnvifProfilesMedia1(readResourceFile("profiles_media1.xml")).map { it.token },
            parseOnvifProfiles(MediaService.MEDIA1, readResourceFile("profiles_media1.xml")).map { it.token },
        )
        assertEquals(
            parseOnvifStreamUriMedia1(readResourceFile("stream_media1.xml")),
            parseOnvifStreamUri(MediaService.MEDIA1, readResourceFile("stream_media1.xml")),
        )
        assertEquals(
            parseOnvifSnapshotUriMedia1(readResourceFile("snapshot_media1.xml")),
            parseOnvifSnapshotUri(MediaService.MEDIA1, readResourceFile("snapshot_media1.xml")),
        )
    }
}
