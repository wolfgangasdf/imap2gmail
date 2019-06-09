import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

val kotlinversion = "1.3.31"

buildscript {
    repositories {
        mavenCentral()
        jcenter() // shadowJar
    }
}

group = "com.wolle"
version = "1.0-SNAPSHOT"

plugins {
    kotlin("jvm") version "1.3.31"
    id("idea")
    id("application")
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.github.johnrengelman.shadow") version "5.0.0"
}

tasks.withType<Wrapper> {
    gradleVersion = "5.3.1"
}

application {
    mainClassName = "MainKt"
    //defaultTasks = tasks.run
}

tasks.withType<Jar> {
    manifest {
        attributes(mapOf(
                "Description" to "imap2gmail jar",
                "Implementation-Title" to "Imap2Gmail",
                "Implementation-Version" to version,
                "Main-Class" to "MainKt"
        ))
    }
}

tasks.withType<ShadowJar> {
    // uses manifest from above!
    baseName = "imap2gmail"
    classifier = ""
    version = ""
    mergeServiceFiles() // essential to enable flac etc
}

//////////////////


repositories {
    mavenCentral()
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinversion")
    compile("org.jetbrains.kotlin:kotlin-reflect:$kotlinversion")
    compile("io.github.microutils:kotlin-logging:1.6.26")
    compile("org.slf4j:slf4j-simple:1.8.0-beta4") // no colors, everything stderr
    compile("javax.mail:javax.mail-api:1.6.2")
    compile("com.sun.mail:javax.mail:1.6.2")

    // https://developers.google.com/gmail/api/quickstart/java
    compile("com.google.api-client:google-api-client:1.29.2")
    compile("com.google.oauth-client:google-oauth-client-jetty:1.30.1")
    compile("com.google.apis:google-api-services-gmail:v1-rev20190422-1.28.0")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

task("dist") {
    dependsOn("shadowJar") // fat jar
}

