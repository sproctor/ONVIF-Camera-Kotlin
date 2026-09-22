package com.seanproctor.onvifcamera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class MediaProfileTest {

    @Test
    fun equalityCoversEveryProperty() {
        val a = MediaProfile("t", "main", "H264", 1920, 1080)
        assertEquals(a, MediaProfile("t", "main", "H264", 1920, 1080))
        assertEquals(a.hashCode(), MediaProfile("t", "main", "H264", 1920, 1080).hashCode())
        assertNotEquals(a, MediaProfile("t", "main", "H264", 1280, 720))
        assertNotEquals(a, MediaProfile("t", "main", "H264"))
    }
}
