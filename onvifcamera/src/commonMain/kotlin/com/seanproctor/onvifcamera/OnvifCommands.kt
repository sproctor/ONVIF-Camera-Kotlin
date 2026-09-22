package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

internal object OnvifCommands {
    /**
     * Serializes a request object as a SOAP 1.2 envelope. xmlutil handles XML escaping and
     * namespace declarations; the output is equivalent to a hand-built body up to whitespace and
     * the namespace prefixes chosen by the serializer.
     */
    private inline fun <reified T : Any> encodeSoap(data: T): String {
        val module = SerializersModule {
            polymorphic(Any::class) {
                subclass(T::class, serializer())
            }
        }
        return SoapXml(module).encodeToString(serializer<Envelope<T>>(), Envelope(data))
    }

    // Media2 returns tokens and names only unless asked for configurations. Only the video
    // encoder is asked for: this library finds cameras and their streams, not audio devices, and
    // every field MediaProfile exposes comes from that one configuration. Media1 has no such
    // choice and inlines everything.
    private val profilesMedia2: String = encodeSoap(GetProfilesRequest(type = listOf("VideoEncoder")))
    private val profilesMedia1: String = encodeSoap(GetProfilesRequest1())

    internal fun profilesCommand(media: MediaService): String = when (media) {
        MediaService.MEDIA2 -> profilesMedia2
        MediaService.MEDIA1 -> profilesMedia1
    }

    internal fun getStreamURICommand(media: MediaService, profile: MediaProfile, protocol: String = "RTSP"): String =
        when (media) {
            MediaService.MEDIA2 -> encodeSoap(GetStreamUriRequest(profileToken = profile.token, protocol = protocol))
            MediaService.MEDIA1 -> encodeSoap(
                GetStreamUriRequest1(
                    streamSetup = StreamSetup(transport = Transport(protocol = protocol)),
                    profileToken = profile.token,
                )
            )
        }

    internal fun getSnapshotURICommand(media: MediaService, profile: MediaProfile): String = when (media) {
        MediaService.MEDIA2 -> encodeSoap(GetSnapshotUriRequest(profileToken = profile.token))
        MediaService.MEDIA1 -> encodeSoap(GetSnapshotUriRequest1(profileToken = profile.token))
    }

    internal val deviceInformationCommand: String = encodeSoap(GetDeviceInformationRequest())

    internal val servicesCommand: String = encodeSoap(GetServicesRequest(includeCapability = false))

    internal val getSystemDateAndTimeCommand: String = encodeSoap(GetSystemDateAndTimeRequest())

    internal val getHostnameCommand: String = encodeSoap(GetHostnameRequest())

    internal fun probeCommand(messageId: String): String {
        return SoapXml().encodeToString(
            ProbeEnvelope.serializer(),
            ProbeEnvelope(header = ProbeHeader(messageId = "uuid:$messageId")),
        )
    }
}
