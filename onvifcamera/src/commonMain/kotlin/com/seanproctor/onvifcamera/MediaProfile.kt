package com.seanproctor.onvifcamera

/**
 * Created by Remy Virin on 05/03/2018.
 * @MediaProfile: is used to store an Onvif media profile (token and name)
 *
 * [width] and [height] are the video encoder's configured resolution in pixels, or
 * null when the profile has no video encoder or the device does not report one.
 */
public data class MediaProfile(
    val token: String,
    val name: String?,
    val encoding: String?,
    val width: Int? = null,
    val height: Int? = null,
) {
    public fun canStream(): Boolean =
            encoding == "MPEG4" || encoding == "H264"

    public fun canSnapshot(): Boolean =
            encoding == "JPEG"
}
