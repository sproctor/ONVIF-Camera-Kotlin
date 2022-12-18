rootProject.name = "onvif-camera-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

plugins {
    // See https://jmfayard.github.io/refreshVersions
    id("de.fayard.refreshVersions") version "0.50.2"
////                            # available:"0.51.0"
}

include(":onvifcamera")
include(":demo")