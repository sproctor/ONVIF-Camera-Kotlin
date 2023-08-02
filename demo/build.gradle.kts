plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
}

kotlin {
    androidTarget()
    jvm()

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

                // SSDP
                implementation("com.seanproctor:lighthouse:_")
            }
        }
        val androidMain by getting {
            dependencies {
                // Android presentation components
                implementation(AndroidX.activity.compose)
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

android {
    namespace = "com.seanproctor.onvifdemo"
    compileSdk = 33
    defaultConfig {
        applicationId = "com.seanproctor.onvifdemo"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        jvmToolchain(11)
    }
}

compose {
    desktop {
        application {
            mainClass = "com.seanproctor.onvifdemo.MainKt"
        }
    }
}
