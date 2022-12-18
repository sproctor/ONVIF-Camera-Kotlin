package com.seanproctor.onvifcamera

import com.seanproctor.onvifcamera.soap.Envelope
import com.seanproctor.onvifcamera.soap.GetServicesResponse
import com.seanproctor.onvifcamera.soap.ProbeMatch
import com.seanproctor.onvifcamera.soap.ProbeMatches
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.serializer
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.serialization.UnknownChildHandler
import nl.adaptivity.xmlutil.serialization.XML

internal expect fun parseOnvifProfiles(input: String): List<MediaProfile>

internal expect fun parseOnvifStreamUri(input: String): String

internal fun parseOnvifServices(input: String): GetServicesResponse {
    val module = SerializersModule {
        polymorphic(Any::class) {
            subclass(GetServicesResponse::class, serializer())
        }
    }

    val xml = XML(module) {
        xmlDeclMode = XmlDeclMode.Minimal
        autoPolymorphic = true
    }
    val serializer = serializer<Envelope<GetServicesResponse>>()

    val result = xml.decodeFromString(serializer, input)

    return result.data
}

@OptIn(ExperimentalXmlUtilApi::class)
internal fun parseOnvifProbeResponse(input: String): List<ProbeMatch> {
    val module = SerializersModule {
        polymorphic(Any::class) {
            subclass(ProbeMatches::class, serializer())
        }
    }

    val xml = XML(module) {
        xmlDeclMode = XmlDeclMode.Minimal
        autoPolymorphic = true
        unknownChildHandler = UnknownChildHandler { _, _, _, _, _ -> emptyList() }
    }
    val serializer = serializer<Envelope<ProbeMatches>>()

    val result = xml.decodeFromString(serializer, input)

    return result.data.matches
}

internal expect fun parseOnvifDeviceInformation(input: String): OnvifDeviceInformation
