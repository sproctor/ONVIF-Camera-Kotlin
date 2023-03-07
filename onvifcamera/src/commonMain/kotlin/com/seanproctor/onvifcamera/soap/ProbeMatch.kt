package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("ProbeMatch", "http://schemas.xmlsoap.org/ws/2005/04/discovery", "d")
internal class ProbeMatch(
    val endpointReference: EndpointReference,
    @XmlElement(true)
    @XmlSerialName("Types", "http://schemas.xmlsoap.org/ws/2005/04/discovery", "d")
    val types: String?,
    @XmlElement(true)
    @XmlSerialName("Scopes", "http://schemas.xmlsoap.org/ws/2005/04/discovery", "d")
    val scopes: String?,
    @XmlElement(true)
    @XmlSerialName("XAddrs", "http://schemas.xmlsoap.org/ws/2005/04/discovery", "d")
    val xaddrs: String?,
)