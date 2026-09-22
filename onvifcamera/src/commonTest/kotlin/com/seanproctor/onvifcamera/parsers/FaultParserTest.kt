package com.seanproctor.onvifcamera.parsers

import com.seanproctor.onvifcamera.parseOnvifFault
import com.seanproctor.onvifcamera.readResourceFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The fault fixtures are not captures. `fault_noProfile.xml` follows the fault example in the
 * ONVIF Core Specification; `fault_gsoap.xml` follows how gSOAP, which most camera firmware is
 * built on, serialises one (single line, `Node`, `Role` and `Detail/Text` present);
 * `fault_appDetail.xml` has the application-defined `Detail` SOAP 1.2 allows, a `ter:Error`
 * tree rather than gSOAP's `Text`.
 */
class FaultParserTest {

    @Test
    fun nestedSubcodesComeOutOutermostFirst() {
        val fault = assertNotNull(parseOnvifFault(readResourceFile("fault_noProfile.xml")))
        assertEquals("Sender", fault.code)
        assertEquals(listOf("InvalidArgVal", "NoProfile"), fault.subcodes)
        assertEquals("The requested profile token does not exist.", fault.reason)
        assertNull(fault.detail)
        assertEquals(
            "Sender/InvalidArgVal/NoProfile: The requested profile token does not exist.",
            fault.message,
        )
    }

    @Test
    fun gsoapExtrasAreToleratedAndDetailIsKept() {
        val fault = assertNotNull(parseOnvifFault(readResourceFile("fault_gsoap.xml")))
        assertEquals("Receiver", fault.code)
        assertEquals(listOf("ActionNotSupported"), fault.subcodes)
        assertEquals("Action Not Implemented", fault.reason)
        assertEquals("The requested action is not implemented by the device.", fault.detail)
    }

    @Test
    fun applicationDefinedDetailIsReducedToItsText() {
        val fault = assertNotNull(parseOnvifFault(readResourceFile("fault_appDetail.xml")))
        assertEquals(listOf("InvalidArgVal", "NoConfig"), fault.subcodes)
        assertEquals(
            "VideoEncoder_9 No video encoder configuration has that token & none can be made.",
            fault.detail,
        )
    }

    @Test
    fun notAuthorizedIsASubcodeLikeAnyOther() {
        val fault = assertNotNull(parseOnvifFault(readResourceFile("fault_notAuthorized.xml")))
        assertEquals(listOf("NotAuthorized"), fault.subcodes)
    }

    @Test
    fun ordinaryResponsesAreNotFaults() {
        assertNull(parseOnvifFault(readResourceFile("profiles.xml")))
        assertNull(parseOnvifFault(readResourceFile("deviceInfo.xml")))
    }

    @Test
    fun somethingThatMentionsFaultButIsNotOneIsNotAFault() {
        assertNull(parseOnvifFault("<Envelope><Body><Fault/></Body></Envelope>"))
        assertNull(parseOnvifFault("Fault"))
    }
}
