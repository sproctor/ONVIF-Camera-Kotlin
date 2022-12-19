import java.util.Properties
import java.net.URI

plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
    id("org.jetbrains.dokka")
}

group = "com.seanproctor"
version = "1.5.0"

val localProperties = Properties().apply {
    load(File(rootProject.rootDir, "local.properties").inputStream())
}

kotlin {
    android {
        publishLibraryVariants("release")
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    jvm  {
        compilations.all {
            kotlinOptions.jvmTarget = "11"
        }
    }

    sourceSets {
        all {
            languageSettings {
                explicitApi()
            }
        }

        val commonMain by getting {
            dependencies {
                api(KotlinX.coroutines.core)

                implementation("io.github.pdvrieze.xmlutil:serialization:0.84.3")
                implementation("io.github.pdvrieze.xmlutil:serialutil:0.84.3")

                implementation("io.ktor:ktor-client-core:_")
                implementation("io.ktor:ktor-client-auth:_")
                implementation("io.ktor:ktor-client-logging:_")
                implementation("org.slf4j:slf4j-api:_")
                implementation("org.jetbrains.kotlinx:atomicfu:_")
                implementation("io.ktor:ktor-network:_")

                implementation("com.benasher44:uuid:_")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-okhttp:_")
                implementation("org.slf4j:slf4j-android:_")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("com.github.kobjects:kxml2:_")
            }
        }

        val jvmTest by getting {

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

val dokkaOutputDir = buildDir.resolve("dokka")

tasks.dokkaHtml.configure {
    outputDirectory.set(dokkaOutputDir)
}

val deleteDokkaOutputDir by tasks.register<Delete>("deleteDokkaOutputDirectory") {
    delete(dokkaOutputDir)
}

val javadocJar = tasks.register<Jar>("javadocJar") {
    dependsOn(deleteDokkaOutputDir, tasks.dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaOutputDir)
}

publishing {
    repositories {
        maven {
            name = "sonatype"
            url = URI("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
            credentials {
                username = localProperties.getProperty("ossrhUsername", "")
                password = localProperties.getProperty("ossrhPassword", "")
            }
        }
    }
    publications.withType<MavenPublication> {
        artifact(javadocJar)
        pom {
            name.set("ONVIF Camera Kotlin")
            description.set("A Kotlin library to interact with ONVIF cameras.")
            url.set("https://github.com/sproctor/ONVIFCameraAndroid")
            licenses {
                license {
                    name.set("MIT")
                    url.set("https://github.com/sproctor/ONVIFCameraAndroid/blob/master/LICENSE")
                }
            }
            developers {
                developer {
                    id.set("sproctor")
                    name.set("Sean Proctor")
                    email.set("sproctor@gmail.com")
                }
            }
            scm {
                url.set("https://github.com/sproctor/ONVIFCameraAndroid/tree/main")
            }
        }
    }
}

ext["signing.keyId"] = localProperties.getProperty("signing.keyId", "")
ext["signing.password"] = localProperties.getProperty("signing.password", "")
ext["signing.secretKeyRingFile"] =
    localProperties.getProperty("signing.secretKeyRingFile", "")

signing {
    sign(publishing.publications)
}