package com.seanproctor.onvifcamera

/**
 * Base of every failure this library raises about a device. Catch this to handle any of them;
 * catch a subclass to handle one.
 */
public sealed class OnvifException(message: String) : Exception(message)

/** The device answered 401: the credentials are missing or wrong. */
public class OnvifUnauthorized(message: String) : OnvifException(message)

/** The device answered 403: the credentials are right but do not permit the operation. */
public class OnvifForbidden(message: String) : OnvifException(message)

/** The device answered with a status other than 2xx, 401 or 403. */
public class OnvifInvalidResponse(message: String) : OnvifException(message)

/**
 * The device's `GetServices` reply did not advertise the service an operation lives under, so
 * there is no endpoint to send it to.
 *
 * @property namespace the WSDL namespace of the missing service, such as
 *   `http://www.onvif.org/ver20/media/wsdl`
 */
public class OnvifServiceUnavailable(public val namespace: String) :
    OnvifException("Device does not offer the service $namespace")
