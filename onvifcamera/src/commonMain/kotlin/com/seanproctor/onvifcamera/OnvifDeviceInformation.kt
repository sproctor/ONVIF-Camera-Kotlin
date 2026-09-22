package com.seanproctor.onvifcamera

/**
 * The reply to `GetDeviceInformation` from
 * https://www.onvif.org/ver10/device/wsdl/devicemgmt.wsdl.
 *
 * Not a data class, for the same reason as [MediaProfile].
 *
 * @property manufacturer the manufacturer of the device
 * @property model the device model
 * @property firmwareVersion the firmware version in the device
 * @property serialNumber the serial number of the device
 * @property hardwareId the hardware ID of the device
 */
public class OnvifDeviceInformation(
    public val manufacturer: String,
    public val model: String,
    public val firmwareVersion: String,
    public val serialNumber: String,
    public val hardwareId: String,
) {
    override fun equals(other: Any?): Boolean =
        other is OnvifDeviceInformation &&
            manufacturer == other.manufacturer &&
            model == other.model &&
            firmwareVersion == other.firmwareVersion &&
            serialNumber == other.serialNumber &&
            hardwareId == other.hardwareId

    override fun hashCode(): Int {
        var result = manufacturer.hashCode()
        result = 31 * result + model.hashCode()
        result = 31 * result + firmwareVersion.hashCode()
        result = 31 * result + serialNumber.hashCode()
        result = 31 * result + hardwareId.hashCode()
        return result
    }

    override fun toString(): String = (
        "Device information:\n"
            + "Manufacturer: $manufacturer\n"
            + "Model: $model\n"
            + "FirmwareVersion: $firmwareVersion\n"
            + "SerialNumber: $serialNumber\n"
            + "HardwareId: $hardwareId\n"
        )
}
