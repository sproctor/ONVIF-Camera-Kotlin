package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("Version", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class Version(
    @XmlElement(true)
    @XmlSerialName("Major", "http://www.onvif.org/ver10/schema", "tt")
    val major: String,
    @XmlElement(true)
    @XmlSerialName("Minor", "http://www.onvif.org/ver10/schema", "tt")
    val minor: String,
)