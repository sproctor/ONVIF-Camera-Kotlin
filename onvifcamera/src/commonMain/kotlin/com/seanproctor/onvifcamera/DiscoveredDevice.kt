package com.seanproctor.onvifcamera

public data class DiscoveredOnvifDevice(
    val id: String,
    val types: List<String>,
    val scopes: List<String>,
    val addresses: List<String>,
)
