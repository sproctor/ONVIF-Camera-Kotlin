package com.seanproctor.onvifcamera

/**
 * Created from https://www.onvif.org/ver10/device/wsdl/devicemgmt.wsdl
 *
 * GetDeviceInformation
 * Description:
 * This operation gets basic device information from the device.
 *
 * @param manufacturerName The manufactor of the device.
 * @param modelName The device model.
 * @param fwVersion The firmware version in the device.
 * @param serialNumber The serial number of the device.
 * @param hwID The hardware ID of the device.
 */

public data class OnvifDeviceInformation(
    val manufacturer: String,
    val model: String,
    val firmwareVersion: String,
    val serialNumber: String,
    val hardwareId: String,
) {
    override fun toString(): String = (
            "Device information:\n"
                    + "Manufacturer: $manufacturer\n"
                    + "Model: $model\n"
                    + "FirmwareVersion: $firmwareVersion\n"
                    + "SerialNumber: $serialNumber\n"
                    + "HardwareId: $hardwareId\n"
            )
}
