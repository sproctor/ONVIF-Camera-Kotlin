package com.seanproctor.onvifcamera

/**
 * A device that answered a WS-Discovery probe.
 *
 * @property id the device's endpoint reference address, stable across reboots and address changes
 * @property types the WS-Discovery types the device advertises, such as `dn:NetworkVideoTransmitter`
 * @property scopes the device's scope URIs, which carry its name, location, hardware and profiles
 * @property addresses candidate device-service URLs. When the device reports a stale address, the
 *   same URL on the address its reply actually came from is listed first.
 *
 * Not a data class, for the same reason as [MediaProfile].
 */
public class DiscoveredOnvifDevice(
    public val id: String,
    public val types: List<String>,
    public val scopes: List<String>,
    public val addresses: List<String>,
) {
    override fun equals(other: Any?): Boolean =
        other is DiscoveredOnvifDevice &&
            id == other.id &&
            types == other.types &&
            scopes == other.scopes &&
            addresses == other.addresses

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + types.hashCode()
        result = 31 * result + scopes.hashCode()
        result = 31 * result + addresses.hashCode()
        return result
    }

    override fun toString(): String =
        "DiscoveredOnvifDevice(id=$id, types=$types, scopes=$scopes, addresses=$addresses)"
}
