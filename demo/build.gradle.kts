plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

android {
    compileSdk = 33
    defaultConfig {
        applicationId = "com.seanproctor.android"
        minSdk = 24
        targetSdk = 33
        versionCode = 1
        versionName = "1.0-SNAPSHOT"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}

kotlin {
    android()
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":onvifcamera"))

                implementation("io.ktor:ktor-client-core:_")
                implementation("io.ktor:ktor-client-cio:_")
                implementation("io.ktor:ktor-client-auth:_")
                implementation("io.ktor:ktor-client-logging:_")

                // Compose dependencies
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material)

                // MOKO ModelView
                api("dev.icerock.moko:mvvm-core:_")
                api("dev.icerock.moko:mvvm-flow:_")

                // Logging
                implementation("io.github.aakira:napier:_")
            }
        }
        val androidMain by getting {
            dependencies {
                // Android presentation components
                implementation(AndroidX.activity.compose)
                //    implementation("org.videolan.android:libvlc-all:_")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:_")
                implementation("dev.icerock.moko:mvvm-flow-compose:_")
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

compose {
    desktop {
        application {
            mainClass = "com.seanproctor.onvifdemo.MainKt"
        }
    }
}
