package com.seanproctor.onvifcamera

/**
 * The device-service operations and where to send them. Media operations are not here: their
 * service is chosen per device, see [MediaService].
 */
internal enum class OnvifRequestType {

    GetServices,
    GetDeviceInformation;

    fun namespace(): String =
        when (this) {
            GetServices, GetDeviceInformation -> "http://www.onvif.org/ver10/device/wsdl"
        }
}
