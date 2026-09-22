package com.seanproctor.onvifcamera

/**
 * Base of every failure this library raises about a device. Catch this to handle any of them;
 * catch a subclass to handle one.
 *
 * Deliberately not sealed. The constructor is internal, so every instance still comes from this
 * library, but a `when` over the subclasses needs an `else`: the set is not closed, and a sealed
 * base would turn each new subclass into a compile error for consumers, or a runtime
 * `NoWhenBranchMatchedException` for ones already compiled.
 */
public abstract class OnvifException internal constructor(message: String) : Exception(message)

/**
 * The device answered 401, or with a SOAP fault whose subcode is `NotAuthorized`: the
 * credentials are missing or wrong.
 */
public class OnvifUnauthorized(message: String) : OnvifException(message)

/** The device answered 403: the credentials are right but do not permit the operation. */
public class OnvifForbidden(message: String) : OnvifException(message)

/** The device answered with a status other than 2xx, 401 or 403, and no SOAP fault. */
public class OnvifInvalidResponse(message: String) : OnvifException(message)

/**
 * The device answered with a SOAP fault. This is how a device rejects an operation it
 * understood: a profile token it does not know, a snapshot it cannot supply, an argument out
 * of range. Some devices send it with HTTP 200, so it is detected on every response.
 *
 * The codes are the local names of the fault's QNames, with the namespace prefixes dropped.
 * The ONVIF subcodes all live in `http://www.onvif.org/ver10/error` and are listed in the
 * ONVIF Core Specification.
 *
 * @property code the SOAP fault code, normally `Sender` (the request was wrong) or `Receiver`
 *   (the device could not carry it out), or null if the device omitted it
 * @property subcodes the fault subcodes, outermost first, such as `["InvalidArgVal", "NoProfile"]`;
 *   empty if the device gave none
 * @property reason the device's human-readable reason, in the first language it offered
 * @property detail the fault's detail text, which gSOAP-based devices fill in
 */
public class OnvifFault(
    public val code: String?,
    public val subcodes: List<String>,
    public val reason: String?,
    public val detail: String?,
) : OnvifException(describe(code, subcodes, reason, detail)) {
    private companion object {
        fun describe(code: String?, subcodes: List<String>, reason: String?, detail: String?): String {
            val codes = (listOfNotNull(code) + subcodes).joinToString("/").ifEmpty { "SOAP fault" }
            val text = reason ?: detail
            return if (text == null) codes else "$codes: $text"
        }
    }
}

/**
 * The device's `GetServices` reply did not advertise the service an operation lives under, so
 * there is no endpoint to send it to.
 *
 * @property namespace the WSDL namespace of the missing service, such as
 *   `http://www.onvif.org/ver20/media/wsdl`
 */
public class OnvifServiceUnavailable(public val namespace: String) :
    OnvifException("Device does not offer the service $namespace")
