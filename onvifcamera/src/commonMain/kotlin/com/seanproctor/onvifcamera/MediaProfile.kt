package com.seanproctor.onvifcamera

/**
 * An ONVIF media profile: the unit a stream or snapshot URI is requested for.
 *
 * [width] and [height] are the video encoder's configured resolution in pixels, or
 * null when the profile has no video encoder or the device does not report one.
 *
 * This is deliberately not a data class. New camera quirks add properties here, and every
 * property added to a data class removes the old `copy` and constructor signatures from the
 * binary, so a consumer built against the previous release fails with `NoSuchMethodError`.
 * A plain class with explicit [equals], [hashCode] and [toString] lets properties be added
 * with default values without breaking anyone.
 */
public class MediaProfile(
    public val token: String,
    public val name: String?,
    public val encoding: String?,
    public val width: Int? = null,
    public val height: Int? = null,
) {
    /** Whether [encoding] is a video codec this library knows a stream URI can be fetched for. */
    public fun canStream(): Boolean =
        encoding == "MPEG4" || encoding == "H264" || encoding == "H265"

    public fun canSnapshot(): Boolean =
        encoding == "JPEG"

    override fun equals(other: Any?): Boolean =
        other is MediaProfile &&
            token == other.token &&
            name == other.name &&
            encoding == other.encoding &&
            width == other.width &&
            height == other.height

    override fun hashCode(): Int {
        var result = token.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + encoding.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + height.hashCode()
        return result
    }

    override fun toString(): String =
        "MediaProfile(token=$token, name=$name, encoding=$encoding, width=$width, height=$height)"
}
