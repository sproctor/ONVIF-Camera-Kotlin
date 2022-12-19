package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("Profiles", "http://www.onvif.org/ver10/media/wsdl", "trt")
internal class Profiles(
    val token: String,
    @XmlElement(true)
    @XmlSerialName("Name", "http://www.onvif.org/ver10/schema", "tt")
    val name: String,
    val encoder: VideoEncoderConfiguration,
)