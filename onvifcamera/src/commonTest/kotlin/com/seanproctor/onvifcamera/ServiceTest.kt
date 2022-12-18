package com.seanproctor.onvifcamera

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ServiceTest {
    @Test
    fun testParser() {
        val input = readResourceFile("services.xml")
        val result = parseOnvifServices(input.decodeToString())
        val service = result.services.find { it.namespace == "http://www.onvif.org/ver10/search/wsdl" }
        assertNotNull(service)
        assertContains(service.address, "/onvif/services")
    }
}