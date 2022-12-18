package com.seanproctor.onvifcamera

import org.junit.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ProbeTest {
    @Test
    fun testParser() {
        val input = readResourceFile("probeResponse.xml")
        val result = parseOnvifProbeResponse(input.decodeToString())
        assertEquals(1, result.size)
    }
}