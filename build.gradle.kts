import edu.wpi.first.toolchain.NativePlatforms
import org.gradle.api.publish.maven.MavenPublication
import io.gitlab.arturbosch.detekt.detekt

plugins {
    kotlin("jvm") version "1.3.11"
    id("edu.wpi.first.GradleRIO") version "2019.1.1"
    id("io.gitlab.arturbosch.detekt") version "1.0.0-RC12"
    maven
    `maven-publish`
}

repositories {
    jcenter()
    maven { setUrl("http://dl.bintray.com/kyonifer/maven") }
}

dependencies {
    // Kotlin Standard Library and Coroutines
    compile(kotlin("stdlib"))
    compile("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.1.0")

    // WPILib
    wpi.deps.wpilib().forEach { compile(it) }
    wpi.deps.vendor.java().forEach { compile(it) }
    wpi.deps.vendor.jni(NativePlatforms.roborio).forEach { nativeZip(it) }
    wpi.deps.vendor.jni(NativePlatforms.desktop).forEach { nativeDesktopZip(it) }

    // Apache Commons Math
    compile("org.apache.commons", "commons-math3", "3.6.1")

    // Unit Testing
    testCompile("org.knowm.xchart", "xchart", "3.2.2")
    testCompile("junit", "junit", "4.12")
}

publishing {
    publications {
        create<MavenPublication>("mavenLocal") {
            groupId = "org.ghrobotics"
            artifactId = "FalconLibrary"
            version = "2019.1.1"

            from(components["java"])
        }
    }
}

detekt {
    config = files("$projectDir/detekt-config.yml")

    reports {
        html {
            enabled = true
            destination = file("$rootDir/detekt.html")
        }
    }
}

tasks.withType<Wrapper>().configureEach {
    gradleVersion = "5.0"
}