/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.knit.build.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("com.gradle.plugin-publish")
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")

    repositories {
        mavenCentral()
    }

    dependencies {
        "implementation"(kotlin("stdlib-jdk8"))
        "implementation"("com.fasterxml.jackson.module:jackson-module-kotlin:2.11.1")
        "implementation"("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.11.1")
        "testImplementation"(kotlin("test-junit"))
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    tasks.withType<KotlinCompile> {
        kotlinOptions.apply {
            languageVersion = "1.4"
            jvmTarget = "1.8"
            allWarningsAsErrors = true
            freeCompilerArgs = freeCompilerArgs + listOf(
                "-Xsuppress-version-warnings", // suppress deprecated 1.4
                "-opt-in=kotlin.RequiresOptIn" // remove after updating languageVersion to 1.7
            )
        }
    }

    // Set version when deploying
    properties["DeployVersion"]?.let { version = it }
}
