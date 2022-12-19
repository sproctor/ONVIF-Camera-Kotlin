package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("GetProfilesResponse", "http://www.onvif.org/ver10/media/wsdl", "trt")
internal class GetProfilesResponse(
    val profiles: List<Profiles>
)