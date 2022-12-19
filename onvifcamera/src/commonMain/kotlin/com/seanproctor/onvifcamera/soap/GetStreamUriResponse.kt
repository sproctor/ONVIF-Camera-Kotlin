package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("GetStreamUriResponse", "http://www.onvif.org/ver20/media/wsdl", "tr2")
internal class GetStreamUriResponse(
    @XmlElement(true)
    @XmlSerialName("Uri", "http://www.onvif.org/ver20/media/wsdl", "tr2")
    val uri: String
)