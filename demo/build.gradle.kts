plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    androidTarget()
    jvm()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":onvifcamera"))

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)

                // Compose dependencies
                implementation(compose.material3)

                implementation(libs.lifecycle.viewmodel.compose)

                // Logging
                implementation(libs.napier)
            }
        }
        val androidMain by getting {
            dependencies {
                // Android presentation components
                implementation(libs.androidx.activity.compose)
                implementation(libs.exoplayer)
                implementation(libs.exoplayer.rtsp)
                implementation(libs.media3.ui)
            }
        }
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.seanproctor.onvifdemo"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.seanproctor.onvifdemo"
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

compose {
    desktop {
        application {
            mainClass = "com.seanproctor.onvifdemo.MainKt"
        }
    }
}
