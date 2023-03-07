package com.seanproctor.onvifcamera

public data class DiscoveredOnvifDevice(
    val address: String,
    val types: List<String>,
    val scopes: List<String>,
    val uri: String,
)
