/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

import java.util.*

plugins {
    `kotlin-dsl`
    kotlin("jvm") version embeddedKotlinVersion
}

dependencies {
    implementation(platform(libs.kotlin.bom))
    implementation(libs.gradlePlugin.kotlin)
    implementation(libs.gradlePlugin.dokka)
}

//val props = Properties().apply {
//    project.file("../gradle.properties").inputStream().use { load(it) }
//}

val projectJvmTarget: String = "11"

kotlin {
    jvmToolchain {
        (this as JavaToolchainSpec).languageVersion.set(JavaLanguageVersion.of(projectJvmTarget))
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = projectJvmTarget
    }
}

kotlinDslPluginOptions {
    jvmTarget.set(projectJvmTarget)
}
