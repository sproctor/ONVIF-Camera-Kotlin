package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

@Serializable
@XmlSerialName("GetDeviceInformationResponse", "http://www.onvif.org/ver10/device/wsdl", "tds")
internal class GetDeviceInformationResponse(
    @XmlElement(true)
    @XmlSerialName("Manufacturer", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val manufacturer: String,
    @XmlElement(true)
    @XmlSerialName("Model", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val model: String,
    @XmlElement(true)
    @XmlSerialName("FirmwareVersion", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val firmwareVersion: String,
    @XmlElement(true)
    @XmlSerialName("SerialNumber", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val serialNumber: String,
    @XmlElement(true)
    @XmlSerialName("HardwareId", "http://www.onvif.org/ver10/device/wsdl", "tds")
    val hardwareId: String,
)