package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("ProbeMatches", "http://schemas.xmlsoap.org/ws/2005/04/discovery", "d")
internal class ProbeMatches(
    val matches: List<ProbeMatch>
)