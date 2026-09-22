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

    // Only the video encoder: this library finds cameras and their streams, not audio devices,
    // and every field MediaProfile exposes comes from that one configuration.
    internal val profilesCommand: String = encodeSoap(GetProfilesRequest(type = listOf("VideoEncoder")))

    internal fun getStreamURICommand(profile: MediaProfile, protocol: String = "RTSP"): String =
        encodeSoap(GetStreamUriRequest(profileToken = profile.token, protocol = protocol))

    internal fun getSnapshotURICommand(profile: MediaProfile): String =
        encodeSoap(GetSnapshotUriRequest(profileToken = profile.token))

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
