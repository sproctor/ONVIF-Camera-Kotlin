package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("Resolution", "http://www.onvif.org/ver10/schema", "tt")
internal class Resolution(
    @XmlElement(true)
    @XmlSerialName("Width", "http://www.onvif.org/ver10/schema", "tt")
    val width: Int,
    @XmlElement(true)
    @XmlSerialName("Height", "http://www.onvif.org/ver10/schema", "tt")
    val height: Int,
)
