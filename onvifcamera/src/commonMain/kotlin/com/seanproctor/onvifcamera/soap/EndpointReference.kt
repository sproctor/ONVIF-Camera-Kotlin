package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("EndpointReference", "http://schemas.xmlsoap.org/ws/2004/08/addressing", "wsa")
internal class EndpointReference(
    @XmlElement(true)
    @XmlSerialName("Address", "http://schemas.xmlsoap.org/ws/2004/08/addressing", "wsa")
    val address: String,
    @XmlElement(true)
    @XmlSerialName("ReferenceParameters", "http://schemas.xmlsoap.org/ws/2004/08/addressing", "wsa")
    val parameters: String?,
    @XmlElement(true)
    @XmlSerialName("Metadata", "http://schemas.xmlsoap.org/ws/2004/08/addressing", "wsa")
    val metadata: String?,
)