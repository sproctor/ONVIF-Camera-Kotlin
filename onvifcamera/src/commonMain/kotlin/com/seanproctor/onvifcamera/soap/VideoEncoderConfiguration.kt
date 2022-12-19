package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("VideoEncoderConfiguration", "http://www.onvif.org/ver10/schema", "tt")
internal class VideoEncoderConfiguration(
    @XmlElement(true)
    @XmlSerialName("Name", "http://www.onvif.org/ver10/schema", "tt")
    val name: String,
    @XmlElement(true)
    @XmlSerialName("Encoding", "http://www.onvif.org/ver10/schema", "tt")
    val encoding: String,
)