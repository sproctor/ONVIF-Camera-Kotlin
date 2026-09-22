package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

private const val MEDIA20_NS = "http://www.onvif.org/ver20/media/wsdl"
private const val SCHEMA_NS = "http://www.onvif.org/ver10/schema"

/**
 * Media2 `GetProfilesResponse`. Unlike Media1, which inlines every configuration in the profile,
 * Media2 nests the ones the request asked for under `Configurations`; the request asks for the
 * video encoder only, so that is the only child modelled. Everything else a device includes is
 * dropped by the lenient parser.
 */
@Serializable
@XmlSerialName("GetProfilesResponse", MEDIA20_NS, "tr2")
internal class GetProfilesResponse(
    val profiles: List<Profiles> = emptyList(),
)

/** One `tr2:MediaProfile`; the element is named `Profiles` in the response. */
@Serializable
@XmlSerialName("Profiles", MEDIA20_NS, "tr2")
internal class Profiles(
    @XmlElement(false)
    val token: String,
    @XmlElement(true)
    @XmlSerialName("Name", MEDIA20_NS, "tr2")
    val name: String? = null,
    val configurations: Configurations? = null,
)

/** The `tr2:ConfigurationSet`: one optional child per configuration kind. */
@Serializable
@XmlSerialName("Configurations", MEDIA20_NS, "tr2")
internal class Configurations(
    val videoEncoder: VideoEncoder2Configuration? = null,
)

/**
 * A `tt:VideoEncoder2Configuration`, which is what the `VideoEncoder` child carries. The wrapper
 * element is Media2's, its contents are the ONVIF schema's. `Encoding` is a free string here
 * (`H264`, `H265`, `JPEG`, ...), where Media1 had a three-value enum without `H265`. Some
 * devices omit it and the resolution on a profile they cannot stream, hence the defaults.
 */
@Serializable
@XmlSerialName("VideoEncoder", MEDIA20_NS, "tr2")
internal class VideoEncoder2Configuration(
    @XmlElement(false)
    val token: String? = null,
    @XmlElement(true)
    @XmlSerialName("Name", SCHEMA_NS, "tt")
    val name: String? = null,
    @XmlElement(true)
    @XmlSerialName("Encoding", SCHEMA_NS, "tt")
    val encoding: String? = null,
    val resolution: Resolution? = null,
)
