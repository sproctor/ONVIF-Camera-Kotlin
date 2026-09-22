package com.seanproctor.onvifcamera.parsers

import com.seanproctor.onvifcamera.OnvifInvalidResponse
import com.seanproctor.onvifcamera.parseOnvifServices
import com.seanproctor.onvifcamera.readResourceFile
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

/**
 * A 2xx body that is not the reply asked for must surface as [OnvifInvalidResponse], never as
 * the parser's own exception, so callers can rely on catching `OnvifException`.
 */
class InvalidResponseTest {

    @Test
    fun htmlLoginPageIsAnInvalidResponse() {
        val e = assertFailsWith<OnvifInvalidResponse> {
            parseOnvifServices("<html><body><form>Please log in</form></body></html>")
        }
        assertNotNull(e.cause, "the parser's exception is kept as the cause")
    }

    @Test
    fun nonXmlBodyIsAnInvalidResponse() {
        assertFailsWith<OnvifInvalidResponse> {
            parseOnvifServices("Bad Request: unsupported SOAP action")
        }
    }

    @Test
    fun replyToADifferentOperationIsAnInvalidResponse() {
        assertFailsWith<OnvifInvalidResponse> {
            parseOnvifServices(readResourceFile("hostname.xml"))
        }
    }
}
