package com.seanproctor.onvifcamera

public interface OnvifLogger {
    public fun error(message: String, e: Throwable?)
    public fun debug(message: String)
}