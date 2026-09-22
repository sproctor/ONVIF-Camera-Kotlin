plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.vanniktech.maven.publish.base)
    alias(libs.plugins.binary.compatibility.validator)
}

group = "com.seanproctor"
version = "3.0.0"

kotlin {
    androidLibrary {
        minSdk = 23
        compileSdk = 37
        namespace = "com.seanproctor.onvifcamera"
        withHostTest { }
    }
    jvm()
    // Need to change the interfaces and implement sockets on ios first
//    iosArm64()
//    iosSimulatorArm64()

    explicitApi()

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
