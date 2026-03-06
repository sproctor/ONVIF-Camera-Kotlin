import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    android {
        namespace = "com.seanproctor.onvifcamera.demo"
        minSdk = 23
        compileSdk = 36
        withHostTest { }
    }
    jvm()

    jvmToolchain(17)

    sourceSets {
        commonMain {
            dependencies {
                api(project(":onvifcamera"))

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)

                // Compose dependencies
                implementation(compose.material3)
                implementation(libs.material.icons)

                implementation(libs.lifecycle.viewmodel.compose)

                // Logging
                implementation(libs.napier)
            }
        }
        androidMain {
            dependencies {
                // Android presentation components
                implementation(libs.androidx.activity.compose)
                implementation(libs.exoplayer)
                implementation(libs.exoplayer.rtsp)
                implementation(libs.media3.ui)
            }
        }
        jvmMain {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutines.swing)
                implementation(libs.ffmpeg)

                // Platform-specific natives based on current OS
                val currentOs = DefaultNativePlatform.getCurrentOperatingSystem()
                val ffmpegVersion = libs.versions.ffmpeg.get()
                when {
                    currentOs.isLinux ->
                        runtimeOnly("org.bytedeco:ffmpeg:${ffmpegVersion}:linux-x86_64")
                    currentOs.isWindows ->
                        runtimeOnly("org.bytedeco:ffmpeg:${ffmpegVersion}:windows-x86_64")
                    currentOs.isMacOsX -> {
                        runtimeOnly("org.bytedeco:ffmpeg:${ffmpegVersion}:macosx-x86_64")
                        runtimeOnly("org.bytedeco:ffmpeg:${ffmpegVersion}:macosx-arm64")
                    }
                }
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
