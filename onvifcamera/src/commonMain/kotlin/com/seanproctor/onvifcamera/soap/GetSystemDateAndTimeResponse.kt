package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private const val DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
private const val SCHEMA_NS = "http://www.onvif.org/ver10/schema"

/**
 * `GetSystemDateAndTime` is answered without authentication (it is a pre-auth operation in the
 * ONVIF access policy), which is what makes it usable for learning the device's clock before
 * the first authenticated request. Only the UTC time is modelled; the local time with its POSIX
 * time zone string is not worth parsing for that.
 */
@Serializable
@XmlSerialName("GetSystemDateAndTimeResponse", DEVICE_NS, "tds")
internal class GetSystemDateAndTimeResponse(
    val systemDateAndTime: SystemDateAndTime? = null,
)

@Serializable
@XmlSerialName("SystemDateAndTime", DEVICE_NS, "tds")
internal class SystemDateAndTime(
    val utcDateTime: UtcDateTime? = null,
)

@Serializable
@XmlSerialName("UTCDateTime", SCHEMA_NS, "tt")
internal class UtcDateTime(
    val time: TimeOfDay,
    val date: CalendarDate,
)

@Serializable
@XmlSerialName("Time", SCHEMA_NS, "tt")
internal class TimeOfDay(
    @XmlElement(true) @XmlSerialName("Hour", SCHEMA_NS, "tt") val hour: Int,
    @XmlElement(true) @XmlSerialName("Minute", SCHEMA_NS, "tt") val minute: Int,
    @XmlElement(true) @XmlSerialName("Second", SCHEMA_NS, "tt") val second: Int,
)

@Serializable
@XmlSerialName("Date", SCHEMA_NS, "tt")
internal class CalendarDate(
    @XmlElement(true) @XmlSerialName("Year", SCHEMA_NS, "tt") val year: Int,
    @XmlElement(true) @XmlSerialName("Month", SCHEMA_NS, "tt") val month: Int,
    @XmlElement(true) @XmlSerialName("Day", SCHEMA_NS, "tt") val day: Int,
)
