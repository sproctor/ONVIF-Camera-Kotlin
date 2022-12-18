package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("Service", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class OnvifService(
    @XmlElement(true)
    @XmlSerialName("Namespace", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val namespace: String,
    @XmlElement(true)
    @XmlSerialName("XAddr", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val address: String,
    val version: Version,
)