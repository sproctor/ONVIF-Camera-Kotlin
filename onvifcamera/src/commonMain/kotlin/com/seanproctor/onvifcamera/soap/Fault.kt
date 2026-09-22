package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import nl.adaptivity.xmlutil.util.CompactFragment

private const val SOAP_NS = "http://www.w3.org/2003/05/soap-envelope"
private const val XML_NS = "http://www.w3.org/XML/1998/namespace"

/**
 * A SOAP 1.2 fault as ONVIF devices send it: a `Code` whose `Value` is `env:Sender` or
 * `env:Receiver`, nested `Subcode`s carrying the ONVIF error codes (`ter:InvalidArgVal`,
 * `ter:NoProfile`, ...), a `Reason` with one `Text` per language, and an optional `Detail`.
 * Every part is optional here because devices leave parts out; gSOAP-based cameras also add
 * `Node` and `Role`, which the lenient parser ignores.
 */
@Serializable
@XmlSerialName("Fault", SOAP_NS, "S")
internal class Fault(
    val code: FaultCode? = null,
    val reason: FaultReason? = null,
    val detail: FaultDetail? = null,
)

@Serializable
@XmlSerialName("Code", SOAP_NS, "S")
internal class FaultCode(
    @XmlElement(true)
    @XmlSerialName("Value", SOAP_NS, "S")
    val value: String,
    val subcode: FaultSubcode? = null,
)

@Serializable
@XmlSerialName("Subcode", SOAP_NS, "S")
internal class FaultSubcode(
    @XmlElement(true)
    @XmlSerialName("Value", SOAP_NS, "S")
    val value: String,
    val subcode: FaultSubcode? = null,
)

@Serializable
@XmlSerialName("Reason", SOAP_NS, "S")
internal class FaultReason(
    val text: List<FaultText> = emptyList(),
)

@Serializable
@XmlSerialName("Text", SOAP_NS, "S")
internal class FaultText(
    @XmlSerialName("lang", XML_NS, "xml")
    val lang: String? = null,
    @XmlValue(true)
    val value: String = "",
)

/**
 * SOAP 1.2 leaves the contents of `Detail` to the application: gSOAP writes a `Text` element,
 * other stacks write their own, such as `ter:Error` with children. The whole fragment is kept
 * and reduced to its text when the fault is reported, so the message reads the same either way.
 */
@Serializable
@XmlSerialName("Detail", SOAP_NS, "S")
internal class FaultDetail(
    @XmlValue(true)
    val content: CompactFragment = CompactFragment(""),
)
