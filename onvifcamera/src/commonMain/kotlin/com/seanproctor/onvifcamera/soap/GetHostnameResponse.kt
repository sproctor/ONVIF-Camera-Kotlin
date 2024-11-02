package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("GetHostnameResponse", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class GetHostnameResponse(
    val hostnameInformation: HostnameInformation
)