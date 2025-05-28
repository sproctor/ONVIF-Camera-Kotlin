import com.vanniktech.maven.publish.MavenPublishBaseExtension
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.compose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.vanniktech.maven.publish.base) apply false
}

allprojects {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }

    // Credentials must be added to ~/.gradle/gradle.properties per
    // https://vanniktech.github.io/gradle-maven-publish-plugin/central/#secrets
    plugins.withId("com.vanniktech.maven.publish.base") {
        configure<PublishingExtension> {
            repositories {
                maven {
                    name = "testMaven"
                    url = layout.buildDirectory.file("testMaven").get().asFile.toURI()
                }
            }
        }
        configure<MavenPublishBaseExtension> {
            publishToMavenCentral(SonatypeHost.S01)
            signAllPublications()
            pom {
                name.set("ONVIF Camera Kotlin")
                description.set("A Kotlin library to interact with ONVIF cameras.")
                url.set("https://github.com/sproctor/ONVIF-Camera-Kotlin/")
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
                    url.set("https://github.com/sproctor/ONVIF-Camera-Kotlin/tree/master")
                }
            }
        }
    }
}

tasks.wrapper {
    gradleVersion = "8.12.1"
}
