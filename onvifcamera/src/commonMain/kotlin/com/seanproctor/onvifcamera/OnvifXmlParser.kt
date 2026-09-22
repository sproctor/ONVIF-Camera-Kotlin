package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.*
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.util.CompactFragment

private inline fun <reified T : Any> parseSoap(input: String): T {
    val module = SerializersModule {
        polymorphic(Any::class) {
            subclass(T::class, serializer())
        }
    }

    val serializer = serializer<Envelope<T>>()

    return try {
        SoapXml(module).decodeFromString(serializer, input).data
    } catch (e: Exception) {
        throw notTheExpectedReply(T::class.simpleName, e)
    }
}

/**
 * A 2xx body that does not decode as the reply asked for: an HTML login page from the camera's
 * web server, a captive portal, a proxy's error page. Decoding is all [parseSoap] does, so any
 * exception out of it means exactly that, and xmlutil is not consistent about which it throws:
 * `XmlException` (an `IOException`, which would read as a network failure) for malformed XML,
 * `SerializationException` for the wrong structure, and a bare `IllegalStateException` from its
 * parser for a body that is not XML at all. All become [OnvifInvalidResponse], so everything the
 * library raises about a device is an [OnvifException], as its API documents.
 */
private fun notTheExpectedReply(expected: String?, cause: Exception): OnvifInvalidResponse =
    OnvifInvalidResponse("Response is not a valid $expected: ${cause.message}")
        .apply { initCause(cause) }

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
        detail = fault.detail?.content?.let(::detailText),
    )
}

/** `ter:NoProfile` -> `NoProfile`. Fault codes are QNames; the prefix is the device's choice. */
private fun String.localName(): String = trim().substringAfterLast(':')

/**
 * The text of a fault's `Detail`, whatever elements the device wrapped it in: SOAP 1.2 leaves
 * that to the application, so gSOAP's `<S:Text>` and a vendor's `<ter:Error>` tree both reduce
 * to their text nodes, entities decoded and whitespace collapsed. Null when there is none.
 */
private fun detailText(fragment: CompactFragment): String? {
    val reader = fragment.getXmlReader()
    val text = try {
        buildString {
            while (reader.hasNext()) {
                when (reader.next()) {
                    EventType.TEXT, EventType.CDSECT -> append(reader.text)
                    // Indentation between elements is reported as ignorable and dropped, so
                    // an element boundary separates the runs of text; collapsed below.
                    else -> append(' ')
                }
            }
        }
    } finally {
        reader.close()
    }
    return text.replace(WHITESPACE, " ").trim().ifEmpty { null }
}

private val WHITESPACE = Regex("\\s+")

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
