package com.seanproctor.onvifcamera

public class OnvifUnauthorized(message: String) : Exception(message)

public class OnvifInvalidResponse(message: String) : Exception(message)

public class OnvifForbidden(message: String) : Exception(message)