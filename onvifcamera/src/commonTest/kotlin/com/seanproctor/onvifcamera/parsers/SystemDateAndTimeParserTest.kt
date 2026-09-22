package com.seanproctor.onvifcamera.parsers

import com.seanproctor.onvifcamera.parseOnvifSystemDateAndTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class SystemDateAndTimeParserTest {

    @Test
    fun deviceUtcTimeIsRead() {
        val reply = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema"><s:Body>
            <tds:GetSystemDateAndTimeResponse><tds:SystemDateAndTime><tt:DateTimeType>NTP</tt:DateTimeType><tt:DaylightSavings>false</tt:DaylightSavings>
            <tt:TimeZone><tt:TZ>CST6CDT,M3.2.0,M11.1.0</tt:TZ></tt:TimeZone>
            <tt:UTCDateTime><tt:Time><tt:Hour>12</tt:Hour><tt:Minute>34</tt:Minute><tt:Second>56</tt:Second></tt:Time><tt:Date><tt:Year>2026</tt:Year><tt:Month>9</tt:Month><tt:Day>22</tt:Day></tt:Date></tt:UTCDateTime>
            <tt:LocalDateTime><tt:Time><tt:Hour>7</tt:Hour><tt:Minute>34</tt:Minute><tt:Second>56</tt:Second></tt:Time><tt:Date><tt:Year>2026</tt:Year><tt:Month>9</tt:Month><tt:Day>22</tt:Day></tt:Date></tt:LocalDateTime>
            </tds:SystemDateAndTime></tds:GetSystemDateAndTimeResponse></s:Body></s:Envelope>
        """.trimIndent()
        assertEquals(Instant.parse("2026-09-22T12:34:56Z"), parseOnvifSystemDateAndTime(reply))
    }

    @Test
    fun aDeviceWithoutUtcTimeGivesNull() {
        val reply = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns:tt="http://www.onvif.org/ver10/schema"><s:Body>
            <tds:GetSystemDateAndTimeResponse><tds:SystemDateAndTime><tt:DateTimeType>Manual</tt:DateTimeType><tt:DaylightSavings>false</tt:DaylightSavings></tds:SystemDateAndTime></tds:GetSystemDateAndTimeResponse>
            </s:Body></s:Envelope>
        """.trimIndent()
        assertNull(parseOnvifSystemDateAndTime(reply))
    }
}
