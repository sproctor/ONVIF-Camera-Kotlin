package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("HostnameInformation", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class HostnameInformation(
    @XmlElement(true)
    @XmlSerialName("FromDHCP", "http://www.onvif.org/ver10/schema", "tt")
    val fromDHCP: Boolean,
    @XmlElement(true)
    @XmlSerialName("Name", "http://www.onvif.org/ver10/schema", "tt")
    val name: String?,
)