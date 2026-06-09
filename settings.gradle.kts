rootProject.name = "onvif-camera-kotlin"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("de.fayard.refreshVersions") version "0.60.6"
}

include(":onvifcamera")
include(":demo")
include(":androidDemo")

refreshVersions {
    rejectVersionIf {
        candidate.stabilityLevel.isLessStableThan(current.stabilityLevel)
    }
}
