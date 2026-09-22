package com.seanproctor.onvifcamera

/**
 * Created by Remy Virin on 05/03/2018.
 * @MediaProfile: is used to store an Onvif media profile (token and name)
 *
 * [width] and [height] are the video encoder's configured resolution in pixels, or
 * null when the profile has no video encoder or the device does not report one.
 *
 * Warning: adding these properties is binary incompatible with earlier versions of this
 * library. Source compatibility is preserved by the default values, but the JVM signatures
 * `MediaProfile(String, String, String)` and `copy(String, String, String)` no longer exist,
 * so code compiled against an older release throws `NoSuchMethodError` against this one.
 * Consumers must recompile rather than swapping the jar in.
 */
public data class MediaProfile(
    val token: String,
    val name: String?,
    val encoding: String?,
    val width: Int? = null,
    val height: Int? = null,
) {
    public fun canStream(): Boolean =
            encoding == "MPEG4" || encoding == "H264" || encoding == "H265"

    public fun canSnapshot(): Boolean =
            encoding == "JPEG"
}
