package com.seanproctor.onvifcamera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class MediaProfileTest {

    private fun profile(encoding: String?) = MediaProfile(token = "t", name = null, encoding = encoding)

    @Test
    fun streamableEncodingsAreTheVideoCodecs() {
        assertTrue(profile("H264").canStream())
        assertTrue(profile("H265").canStream())
        assertTrue(profile("MPEG4").canStream())
        assertFalse(profile("JPEG").canStream())
        assertFalse(profile(null).canStream())
    }

    @Test
    fun snapshotEncodingIsJpegOnly() {
        assertTrue(profile("JPEG").canSnapshot())
        assertFalse(profile("H264").canSnapshot())
    }

    @Test
    fun equalityCoversEveryProperty() {
        val a = MediaProfile("t", "main", "H264", 1920, 1080)
        assertEquals(a, MediaProfile("t", "main", "H264", 1920, 1080))
        assertEquals(a.hashCode(), MediaProfile("t", "main", "H264", 1920, 1080).hashCode())
        assertNotEquals(a, MediaProfile("t", "main", "H264", 1280, 720))
        assertNotEquals(a, MediaProfile("t", "main", "H264"))
    }
}
