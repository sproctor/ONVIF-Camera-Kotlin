package com.seanproctor.onvifcamera.network

import kotlin.test.Test
import kotlin.test.assertEquals

class SenderAddressTest {
    @Test
    fun staleXAddrIsOfferedOnTheSenderAddressFirst() {
        assertEquals(
            listOf("http://192.168.4.48/onvif/device_service", "http://192.168.4.56/onvif/device_service"),
            withSenderAddress(listOf("http://192.168.4.56/onvif/device_service"), "192.168.4.48"),
        )
    }

    @Test
    fun matchingXAddrIsLeftAlone() {
        val xaddrs = listOf("http://192.168.4.48:8080/onvif/device_service")
        assertEquals(xaddrs, withSenderAddress(xaddrs, "192.168.4.48"))
    }

    @Test
    fun portIsKeptAndIpv6XAddrsAreNotRewritten() {
        assertEquals(
            listOf("http://10.0.0.5:8000/onvif", "http://10.0.0.9:8000/onvif", "http://[fe80::1]/onvif"),
            withSenderAddress(listOf("http://10.0.0.9:8000/onvif", "http://[fe80::1]/onvif"), "10.0.0.5"),
        )
    }

    @Test
    fun ipv6SenderChangesNothing() {
        val xaddrs = listOf("http://10.0.0.9/onvif")
        assertEquals(xaddrs, withSenderAddress(xaddrs, "fe80::1%eth0"))
    }
}
