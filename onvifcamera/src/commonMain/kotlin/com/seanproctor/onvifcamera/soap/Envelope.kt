package com.seanproctor.onvifcamera.soap

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName

/**
 * A SOAP 1.2 envelope: an optional [Header], which for requests carries WS-Security when the
 * device needs credentials, and a [Body] wrapping one message.
 *
 * @property data the message in the body
 * @param BODYTYPE SOAP is a generic protocol and the wrapper does not depend on a particular
 *   body type, so it is parameterised (which works with kotlinx serialization).
 */
@Serializable
@XmlSerialName("Envelope", "http://www.w3.org/2003/05/soap-envelope", "S")
internal class Envelope<BODYTYPE> private constructor(
    private val header: Header? = null,
    private val body: Body<BODYTYPE>,
) {
    constructor(data: BODYTYPE, security: Security? = null) : this(security?.let(::Header), Body(data))

    val data: BODYTYPE get() = body.data

    override fun toString(): String = "Envelope(header=$header, body=$body)"

    /**
     * The SOAP standard requires the body to wrap a single element. The content is polymorphic
     * so one envelope type serves every message.
     */
    @Serializable
    @XmlSerialName("Body", "http://www.w3.org/2003/05/soap-envelope", "S")
    private data class Body<BODYTYPE>(@Polymorphic val data: BODYTYPE)
}
