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

        jvmTest {
            dependencies {
                // The library ships only ktor-client-core; the app supplies the engine. The
                // conformance suite talks real HTTP to its fake device, so it needs one here.
                implementation(libs.ktor.client.cio)
            }
        }
    }

    jvmToolchain(17)
}

// Client conformance suite (onvifcamera/src/jvmTest/.../conformance): the library end to end
// against a fake ONVIF device that checks every request against the specification, plus the
// same checks against a real camera when ONVIF_CONFORMANCE_URL is set. Deliberately not part of
// check: run it on demand, and again after any large change.
//   ./gradlew :onvifcamera:conformanceTest
val jvmTest = tasks.named<Test>("jvmTest") {
    filter { excludeTestsMatching("com.seanproctor.onvifcamera.conformance.*") }
}
tasks.register<Test>("conformanceTest") {
    group = "verification"
    description = "Runs the ONVIF client conformance suite (not part of check)."
    dependsOn("jvmTestClasses")
    testClassesDirs = jvmTest.get().testClassesDirs
    classpath = jvmTest.get().classpath
    filter { includeTestsMatching("com.seanproctor.onvifcamera.conformance.*") }
    listOf(
        "ONVIF_CONFORMANCE_URL",
        "ONVIF_CONFORMANCE_USERNAME",
        "ONVIF_CONFORMANCE_PASSWORD",
        "ONVIF_CONFORMANCE_DISCOVERY",
    ).forEach { name -> providers.environmentVariable(name).orNull?.let { environment(name, it) } }
    outputs.upToDateWhen { false }
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty())
    )
}
