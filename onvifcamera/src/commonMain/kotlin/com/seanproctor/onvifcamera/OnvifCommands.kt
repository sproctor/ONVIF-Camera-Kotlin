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
    private inline fun <reified T : Any> encodeSoap(data: T, security: Security? = null): String {
        val module = SerializersModule {
            polymorphic(Any::class) {
                subclass(T::class, serializer())
            }
        }
        return SoapXml(module).encodeToString(serializer<Envelope<T>>(), Envelope(data, security))
    }

    // Every authenticated operation takes the WS-Security header for this request; a token is
    // single-use (fresh nonce and timestamp), so nothing here is cached.

    // Media2 returns tokens and names only unless asked for configurations. Only the video
    // encoder is asked for: this library finds cameras and their streams, not audio devices, and
    // every field MediaProfile exposes comes from that one configuration. Media1 has no such
    // choice and inlines everything.
    internal fun profilesCommand(media: MediaService, security: Security? = null): String = when (media) {
        MediaService.MEDIA2 -> encodeSoap(GetProfilesRequest(type = listOf("VideoEncoder")), security)
        MediaService.MEDIA1 -> encodeSoap(GetProfilesRequest1(), security)
    }

    internal fun getStreamURICommand(
        media: MediaService,
        profile: MediaProfile,
        protocol: String = "RTSP",
        security: Security? = null,
    ): String = when (media) {
        MediaService.MEDIA2 -> encodeSoap(GetStreamUriRequest(profileToken = profile.token, protocol = protocol), security)
        MediaService.MEDIA1 -> encodeSoap(
            GetStreamUriRequest1(
                streamSetup = StreamSetup(transport = Transport(protocol = protocol)),
                profileToken = profile.token,
            ),
            security,
        )
    }

    internal fun getSnapshotURICommand(media: MediaService, profile: MediaProfile, security: Security? = null): String =
        when (media) {
            MediaService.MEDIA2 -> encodeSoap(GetSnapshotUriRequest(profileToken = profile.token), security)
            MediaService.MEDIA1 -> encodeSoap(GetSnapshotUriRequest1(profileToken = profile.token), security)
        }

    internal fun deviceInformationCommand(security: Security? = null): String =
        encodeSoap(GetDeviceInformationRequest(), security)

    internal fun servicesCommand(security: Security? = null): String =
        encodeSoap(GetServicesRequest(includeCapability = false), security)

    // Pre-auth operations in the ONVIF access policy: never sent with credentials.
    internal val getSystemDateAndTimeCommand: String = encodeSoap(GetSystemDateAndTimeRequest())

    internal val getHostnameCommand: String = encodeSoap(GetHostnameRequest())

    internal fun probeCommand(messageId: String): String {
        return SoapXml().encodeToString(
            ProbeEnvelope.serializer(),
            ProbeEnvelope(header = ProbeHeader(messageId = "uuid:$messageId")),
        )
    }
}
