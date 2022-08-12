package com.seanproctor.onvifcamera

/**
 * Created by Remy Virin on 05/03/2018.
 * @MediaProfile: is used to store an Onvif media profile (token and name)
 */
public data class MediaProfile(val name: String, val token: String, val encoding: String) {
    public fun canStream(): Boolean =
            encoding == "MPEG4" || encoding == "H264"

    public fun canSnapshot(): Boolean =
            encoding == "JPEG"
}
