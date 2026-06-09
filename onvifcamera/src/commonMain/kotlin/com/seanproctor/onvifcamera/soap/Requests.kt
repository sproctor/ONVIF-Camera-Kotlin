package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private const val DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl"
private const val MEDIA10_NS = "http://www.onvif.org/ver10/media/wsdl"
private const val MEDIA20_NS = "http://www.onvif.org/ver20/media/wsdl"

@Serializable
@XmlSerialName("GetProfiles", MEDIA10_NS, "")
internal class GetProfilesRequest

@Serializable
@XmlSerialName("GetStreamUri", MEDIA20_NS, "")
internal class GetStreamUriRequest(
    @XmlElement(true)
    @XmlSerialName("ProfileToken", MEDIA20_NS, "")
    val profileToken: String,
    @XmlElement(true)
    @XmlSerialName("Protocol", MEDIA20_NS, "")
    val protocol: String,
)

@Serializable
@XmlSerialName("GetSnapshotUri", MEDIA20_NS, "")
internal class GetSnapshotUriRequest(
    @XmlElement(true)
    @XmlSerialName("ProfileToken", MEDIA20_NS, "")
    val profileToken: String,
)

@Serializable
@XmlSerialName("GetDeviceInformation", DEVICE_NS, "")
internal class GetDeviceInformationRequest

@Serializable
@XmlSerialName("GetServices", DEVICE_NS, "")
internal class GetServicesRequest(
    @XmlElement(true)
    @XmlSerialName("IncludeCapability", DEVICE_NS, "")
    val includeCapability: Boolean,
)

@Serializable
@XmlSerialName("GetSystemDateAndTime", DEVICE_NS, "")
internal class GetSystemDateAndTimeRequest

@Serializable
@XmlSerialName("GetHostname", DEVICE_NS, "")
internal class GetHostnameRequest
