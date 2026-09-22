package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer

private inline fun <reified T : Any> parseSoap(input: String): T {
    val module = SerializersModule {
        polymorphic(Any::class) {
            subclass(T::class, serializer())
        }
    }

    val serializer = serializer<Envelope<T>>()

    return SoapXml(module).decodeFromString(serializer, input).data
}

/**
 * The fault in [input], or null if it is not a SOAP fault. Only a well-formed fault counts:
 * anything that fails to decode as one is treated as not a fault, so the caller falls back
 * to judging the response by its HTTP status.
 */
internal fun parseOnvifFault(input: String): OnvifFault? {
    // Cheap pre-check so the common path does not attempt a decode that is bound to fail.
    if (!input.contains("Fault")) return null
    val fault = try {
        parseSoap<Fault>(input)
    } catch (_: Exception) {
        return null
    }
    val subcodes = generateSequence(fault.code?.subcode) { it.subcode }
        .map { it.value.localName() }
        .toList()
    return OnvifFault(
        code = fault.code?.value?.localName(),
        subcodes = subcodes,
        reason = fault.reason?.text?.firstOrNull()?.value?.trim()?.ifEmpty { null },
        detail = fault.detail?.text?.trim()?.ifEmpty { null },
    )
}

/** `ter:NoProfile` -> `NoProfile`. Fault codes are QNames; the prefix is the device's choice. */
private fun String.localName(): String = trim().substringAfterLast(':')

internal fun parseOnvifProfiles(input: String): List<MediaProfile> {
    val result = parseSoap<GetProfilesResponse>(input)

    return result.profiles.map {
        MediaProfile(
            token = it.token,
            name = it.name,
            encoding = it.encoder?.encoding,
            width = it.encoder?.resolution?.width,
            height = it.encoder?.resolution?.height,
        )
    }
}

internal fun parseOnvifStreamUri(input: String): String {
    val result = parseSoap<GetStreamUriResponse>(input)
    return result.uri
}

internal fun parseOnvifSnapshotUri(input: String): String {
    val result = parseSoap<GetSnapshotUriResponse>(input)
    return result.uri
}

internal fun parseOnvifServices(input: String): List<OnvifService> {
    return parseSoap<GetServicesResponse>(input).services
}

internal fun parseOnvifGetHostnameResponse(input: String): String? {
    return parseSoap<GetHostnameResponse>(input).hostnameInformation.name
}

internal fun parseOnvifProbeResponse(input: String): List<ProbeMatch> {
    return parseSoap<ProbeMatches>(input).matches
}

internal fun parseOnvifDeviceInformation(input: String): OnvifDeviceInformation {
    val result = parseSoap<GetDeviceInformationResponse>(input)
    return OnvifDeviceInformation(
        manufacturer = result.manufacturer,
        model = result.model,
        firmwareVersion = result.firmwareVersion,
        serialNumber = result.serialNumber,
        hardwareId = result.hardwareId,
    )
}
