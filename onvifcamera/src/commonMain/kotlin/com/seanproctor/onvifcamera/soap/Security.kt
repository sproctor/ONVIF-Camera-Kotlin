package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue

private const val SOAP_NS = "http://www.w3.org/2003/05/soap-envelope"
internal const val WSSE_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd"
internal const val WSU_NS = "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd"
internal const val PASSWORD_DIGEST_TYPE =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest"
internal const val BASE64_BINARY_ENCODING =
    "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary"

/**
 * The SOAP header of an ONVIF request. Only WS-Security is ever sent; when a response carries a
 * header (WS-Addressing on discovery replies, for instance) the lenient parser skips what is not
 * modelled, which is why [security] is optional.
 */
@Serializable
@XmlSerialName("Header", SOAP_NS, "S")
internal class Header(
    val security: Security? = null,
)

/**
 * WS-Security with a UsernameToken, ONVIF's primary authentication (Core Specification 5.12).
 * Deliberately sent without `mustUnderstand`: a device that authenticates at the HTTP layer
 * instead is then free to ignore it rather than obliged to fault.
 */
@Serializable
@XmlSerialName("Security", WSSE_NS, "wsse")
internal class Security(
    val usernameToken: UsernameToken,
)

/** WSS UsernameToken Profile 1.0 with a PasswordDigest: `Base64(SHA-1(nonce + created + password))`. */
@Serializable
@XmlSerialName("UsernameToken", WSSE_NS, "wsse")
internal class UsernameToken(
    @XmlElement(true)
    @XmlSerialName("Username", WSSE_NS, "wsse")
    val username: String,
    val password: Password,
    val nonce: Nonce,
    @XmlElement(true)
    @XmlSerialName("Created", WSU_NS, "wsu")
    val created: String,
)

@Serializable
@XmlSerialName("Password", WSSE_NS, "wsse")
internal class Password(
    @XmlElement(false)
    @XmlSerialName("Type", "", "")
    val type: String,
    @XmlValue(true)
    val value: String,
)

@Serializable
@XmlSerialName("Nonce", WSSE_NS, "wsse")
internal class Nonce(
    @XmlElement(false)
    @XmlSerialName("EncodingType", "", "")
    val encodingType: String,
    @XmlValue(true)
    val value: String,
)
