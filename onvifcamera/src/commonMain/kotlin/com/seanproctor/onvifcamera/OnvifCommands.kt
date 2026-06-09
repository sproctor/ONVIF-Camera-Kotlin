package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.serialization.XML

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
        val xml = XML(module) {
            autoPolymorphic = true
        }
        return xml.encodeToString(serializer<Envelope<T>>(), Envelope(data))
    }

    internal val profilesCommand: String = encodeSoap(GetProfilesRequest())

    internal fun getStreamURICommand(profile: MediaProfile, protocol: String = "RTSP"): String =
        encodeSoap(GetStreamUriRequest(profileToken = profile.token, protocol = protocol))

    internal fun getSnapshotURICommand(profile: MediaProfile): String =
        encodeSoap(GetSnapshotUriRequest(profileToken = profile.token))

    internal val deviceInformationCommand: String = encodeSoap(GetDeviceInformationRequest())

    internal val servicesCommand: String = encodeSoap(GetServicesRequest(includeCapability = false))

    internal val getSystemDateAndTimeCommand: String = encodeSoap(GetSystemDateAndTimeRequest())

    internal val getHostnameCommand: String = encodeSoap(GetHostnameRequest())

    internal fun probeCommand(messageId: String): String {
        val xml = XML { autoPolymorphic = true }
        return xml.encodeToString(
            ProbeEnvelope.serializer(),
            ProbeEnvelope(header = ProbeHeader(messageId = "uuid:$messageId")),
        )
    }
}
