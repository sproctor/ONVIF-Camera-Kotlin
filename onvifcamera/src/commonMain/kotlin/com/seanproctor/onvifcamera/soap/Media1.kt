package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

// Media1 (ver10/media, prefix trt) requests and responses, used when a device does not offer
// Media2. Media1 inlines every configuration in a profile and wraps URIs in a MediaUri; its
// VideoEncoding enumeration is JPEG | MPEG4 | H264, so a camera with an H.265 stream may leave
// Encoding out of that profile altogether.

private const val MEDIA10_NS = "http://www.onvif.org/ver10/media/wsdl"
private const val SCHEMA_NS = "http://www.onvif.org/ver10/schema"

@Serializable
@XmlSerialName("GetProfiles", MEDIA10_NS, "")
internal class GetProfilesRequest1

/** Media1 needs the transport spelled out; the schema orders `StreamSetup` before the token. */
@Serializable
@XmlSerialName("GetStreamUri", MEDIA10_NS, "")
internal class GetStreamUriRequest1(
    val streamSetup: StreamSetup,
    @XmlElement(true)
    @XmlSerialName("ProfileToken", MEDIA10_NS, "")
    val profileToken: String,
)

@Serializable
@XmlSerialName("StreamSetup", MEDIA10_NS, "")
internal class StreamSetup(
    @XmlElement(true)
    @XmlSerialName("Stream", SCHEMA_NS, "tt")
    val stream: String = "RTP-Unicast",
    val transport: Transport,
)

@Serializable
@XmlSerialName("Transport", SCHEMA_NS, "tt")
internal class Transport(
    @XmlElement(true)
    @XmlSerialName("Protocol", SCHEMA_NS, "tt")
    val protocol: String,
)

@Serializable
@XmlSerialName("GetSnapshotUri", MEDIA10_NS, "")
internal class GetSnapshotUriRequest1(
    @XmlElement(true)
    @XmlSerialName("ProfileToken", MEDIA10_NS, "")
    val profileToken: String,
)

@Serializable
@XmlSerialName("GetProfilesResponse", MEDIA10_NS, "trt")
internal class GetProfilesResponse1(
    val profiles: List<Profiles1> = emptyList(),
)

@Serializable
@XmlSerialName("Profiles", MEDIA10_NS, "trt")
internal class Profiles1(
    @XmlElement(false)
    val token: String,
    @XmlElement(true)
    @XmlSerialName("Name", SCHEMA_NS, "tt")
    val name: String? = null,
    val encoder: VideoEncoderConfiguration1? = null,
)

@Serializable
@XmlSerialName("VideoEncoderConfiguration", SCHEMA_NS, "tt")
internal class VideoEncoderConfiguration1(
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

@Serializable
@XmlSerialName("GetStreamUriResponse", MEDIA10_NS, "trt")
internal class GetStreamUriResponse1(
    val mediaUri: MediaUri,
)

@Serializable
@XmlSerialName("GetSnapshotUriResponse", MEDIA10_NS, "trt")
internal class GetSnapshotUriResponse1(
    val mediaUri: MediaUri,
)

/** Media1 wraps a URI with validity hints (`InvalidAfterConnect`, `Timeout`, ...) not kept here. */
@Serializable
@XmlSerialName("MediaUri", MEDIA10_NS, "trt")
internal class MediaUri(
    @XmlElement(true)
    @XmlSerialName("Uri", SCHEMA_NS, "tt")
    val uri: String,
)
