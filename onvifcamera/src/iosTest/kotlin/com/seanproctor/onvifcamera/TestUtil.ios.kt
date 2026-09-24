package com.seanproctor.onvifcamera

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.stringWithContentsOfFile

// The simulator shares the host's file system, so the tests read commonTest/resources in place;
// build.gradle.kts passes its absolute path in ONVIF_TEST_RESOURCES.
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun readResourceFile(filename: String): String {
    val directory = NSProcessInfo.processInfo.environment["ONVIF_TEST_RESOURCES"] as String?
        ?: error("ONVIF_TEST_RESOURCES is not set; run the tests through Gradle")
    return NSString.stringWithContentsOfFile("$directory/$filename", NSUTF8StringEncoding, null)
        ?: error("Could not read $directory/$filename")
}
