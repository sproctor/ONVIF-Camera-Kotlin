plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("maven-publish")
}

group = "com.seanproctor.onvifcamera"
version = "1.4.0"

kotlin {
    android()

    sourceSets {
        all {
            languageSettings {
                optIn("kotlin.RequiresOptIn")
                explicitApi()
            }
        }

        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:_")
                implementation("io.ktor:ktor-client-core:_")
                implementation("io.ktor:ktor-client-auth:_")
                implementation("io.ktor:ktor-client-logging:_")
                implementation("org.slf4j:slf4j-api:_")
                implementation("org.jetbrains.kotlinx:atomicfu:_")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:_")
                implementation("uk.uuid.slf4j:slf4j-android:_")
            }
        }

        val androidTest by getting {
            dependencies {
                implementation("junit:junit:_")
            }
        }
    }
}

android {
    compileSdk = 33

    namespace = "com.seanproctor.onvifcamera"

    defaultConfig {
        minSdk = 21
        targetSdk = 33

        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

//apply from: 'publish.gradle'
