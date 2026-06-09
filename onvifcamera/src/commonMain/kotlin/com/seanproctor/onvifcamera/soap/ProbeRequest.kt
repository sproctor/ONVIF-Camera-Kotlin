package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.QNameSerializer
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

private const val SOAP_NS = "http://www.w3.org/2003/05/soap-envelope"
private const val WSA_NS = "http://schemas.xmlsoap.org/ws/2004/08/addressing"
private const val WSD_NS = "http://schemas.xmlsoap.org/ws/2005/04/discovery"
private const val NETWORK_NS = "http://www.onvif.org/ver10/network/wsdl"

private const val PROBE_ACTION = "http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe"
private const val ANONYMOUS_ROLE = "http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous"
private const val DISCOVERY_TARGET = "urn:schemas-xmlsoap-org:ws:2005:04:discovery"

/**
 * WS-Discovery probe envelope. Unlike the ONVIF operation requests this carries a SOAP header and a
 * namespaced QName as element content, so it does not reuse the generic [Envelope].
 */
@Serializable
@XmlSerialName("Envelope", SOAP_NS, "s")
internal class ProbeEnvelope(
    val header: ProbeHeader,
    val body: ProbeBody = ProbeBody(),
)

@Serializable
@XmlSerialName("Header", SOAP_NS, "s")
internal class ProbeHeader(
    val action: ProbeAction = ProbeAction(),
    @XmlElement(true)
    @XmlSerialName("MessageID", WSA_NS, "a")
    val messageId: String,
    val replyTo: ProbeReplyTo = ProbeReplyTo(),
    val to: ProbeTo = ProbeTo(),
)

@Serializable
@XmlSerialName("Action", WSA_NS, "a")
internal class ProbeAction(
    @XmlElement(false)
    @XmlSerialName("mustUnderstand", SOAP_NS, "s")
    val mustUnderstand: String = "1",
    @XmlValue
    val value: String = PROBE_ACTION,
)

@Serializable
@XmlSerialName("ReplyTo", WSA_NS, "a")
internal class ProbeReplyTo(
    @XmlElement(true)
    @XmlSerialName("Address", WSA_NS, "a")
    val address: String = ANONYMOUS_ROLE,
)

@Serializable
@XmlSerialName("To", WSA_NS, "a")
internal class ProbeTo(
    @XmlElement(false)
    @XmlSerialName("mustUnderstand", SOAP_NS, "s")
    val mustUnderstand: String = "1",
    @XmlValue
    val value: String = DISCOVERY_TARGET,
)

@Serializable
@XmlSerialName("Body", SOAP_NS, "s")
internal class ProbeBody(
    val probe: Probe = Probe(),
)

@Serializable
@XmlSerialName("Probe", WSD_NS, "")
internal class Probe(
    val types: ProbeTypes = ProbeTypes(),
)

@Serializable
@XmlSerialName("Types", WSD_NS, "d")
internal class ProbeTypes(
    @XmlValue
    @Serializable(with = QNameSerializer::class)
    val type: QName = QName(NETWORK_NS, "NetworkVideoTransmitter", "dp0"),
)
