package com.seanproctor.onvifcamera

internal expect fun parseOnvifProfiles(input: String): List<MediaProfile>

internal expect fun parseOnvifStreamUri(input: String): String

internal expect fun parseOnvifServices(input: String): Map<String, String>

internal expect fun parseOnvifDeviceInformation(input: String): OnvifDeviceInformation
