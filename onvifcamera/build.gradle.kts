plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish.base)
}

group = "com.seanproctor"
version = "3.0.0-alpha01"

kotlin {
    android {
        minSdk = 23
        compileSdk = 37
        namespace = "com.seanproctor.onvifcamera"
        // Without this the commonTest suite runs on the JVM target only.
        withHostTest { }
    }
    jvm()
    // Need to change the interfaces and implement sockets on ios first
//    iosArm64()
//    iosSimulatorArm64()

    explicitApi()

    // Kotlin's built-in ABI validation rather than the binary-compatibility-validator plugin:
    // under AGP 9 the plugin never registers tasks for the Android target
    // (Kotlin/binary-compatibility-validator#312), so it could not cover the androidMain
    // factory. Dumps live in api/<target>/; checkKotlinAbi runs as part of check, and
    // updateLegacyAbi regenerates the dumps after a public API change.
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlinx.coroutines.core)

                implementation(libs.xmlutil.serialization)
                implementation(libs.xmlutil.serialutil)

                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.auth)
                implementation(libs.ktor.client.logging)
            }
        }

        commonTest {
            dependencies {
                implementation(kotlin("test"))
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }

    jvmToolchain(17)
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty())
    )
}
