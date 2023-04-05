plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("com.vanniktech.maven.publish.base")
}

group = "com.seanproctor"
version = "1.8.1"

kotlin {
    android {
        publishLibraryVariants("release")
    }

    jvm()

    explicitApi()

    sourceSets {
        val commonMain by getting {
            dependencies {
                api(KotlinX.coroutines.core)

                implementation("io.github.pdvrieze.xmlutil:serialization:_")
                implementation("io.github.pdvrieze.xmlutil:serialutil:_")

                implementation("io.ktor:ktor-client-core:_")
                implementation("io.ktor:ktor-client-auth:_")
                implementation("io.ktor:ktor-client-logging:_")
                implementation("org.slf4j:slf4j-api:_")
                implementation("org.jetbrains.kotlinx:atomicfu:_")
                implementation("io.ktor:ktor-network:_")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.5")

                implementation("com.benasher44:uuid:_")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }

    jvmToolchain(11)
}

android {
    compileSdk = 33

    namespace = "com.seanproctor.onvifcamera"

    defaultConfig {
        minSdk = 21

        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
    configure(
        com.vanniktech.maven.publish.KotlinMultiplatform(javadocJar = com.vanniktech.maven.publish.JavadocJar.Empty())
    )
}
