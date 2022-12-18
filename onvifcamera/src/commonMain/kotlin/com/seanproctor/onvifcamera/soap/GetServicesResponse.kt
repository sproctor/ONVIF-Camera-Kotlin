package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("GetServicesResponse", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class GetServicesResponse(
    @XmlElement(true)
    val services: List<OnvifService>
)