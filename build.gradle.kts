import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinversion = "1.3.61"

buildscript {
    repositories {
        mavenCentral()
    }
}

group = "com.wolle"
version = "1.0-SNAPSHOT"

println("Current Java version: ${JavaVersion.current()}")
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    if (JavaVersion.current().toString() != "13") throw GradleException("Use Java 13")
}

plugins {
    kotlin("jvm") version "1.3.61"
    id("idea")
    application
    id("com.github.ben-manes.versions") version "0.27.0"
    id("org.beryx.runtime") version "1.8.0"
}

application {
    mainClassName = "MainKt"
    applicationDefaultJvmArgs = listOf("-Xmx256m", "-XX:MaxHeapFreeRatio=10", "-XX:MinHeapFreeRatio=10")
}

// enable keyboard input if launched by "gradle run"
val run by tasks.getting(JavaExec::class) {
    standardInput = System.`in`
}

runtime {
    // check "gradle suggestModules", and add jdk.crypto.ec for ssl handshake
    addModules("java.desktop", "java.logging", "java.xml", "java.security.sasl", "java.datatransfer", "jdk.crypto.ec")
    imageZip.set(project.file("${project.buildDir}/image-zip/imap2gmail.zip"))
    options.set(listOf("--strip-debug", "--compress", "2", "--no-header-files", "--no-man-pages"))
    targetPlatform("linux", System.getenv("JDK_LINUX_HOME"))
//    targetPlatform("mac", System.getenv("JDK_MAC_HOME"))
//    targetPlatform("win", System.getenv("JDK_WIN_HOME"))
}

repositories {
    mavenCentral()
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib:$kotlinversion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    compile("io.github.microutils:kotlin-logging:1.7.8")
    compile("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    compile("javax.mail:javax.mail-api:1.6.2")
    compile("com.sun.mail:javax.mail:1.6.2")

    // https://developers.google.com/gmail/api/quickstart/java
    compile("com.google.api-client:google-api-client:1.30.7")
    compile("com.google.oauth-client:google-oauth-client-jetty:1.30.5")
    compile("com.google.apis:google-api-services-gmail:v1-rev20191113-1.30.3")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task("dist") {
    dependsOn("runtimeZip")
}

