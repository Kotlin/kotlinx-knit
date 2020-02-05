/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.knit.build.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    `maven-publish`
}

repositories {
    jcenter()
}

val freemarkerVersion: String by project

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())
    implementation("org.freemarker:freemarker:$freemarkerVersion")
    testImplementation(kotlin("test-junit"))
}

sourceSets {
    main.kotlin.dir = "src"
    test.kotlin.dir = "test"
    main.resources.dir = "resources"
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.apply {
        languageVersion = "1.3"
        jvmTarget = "1.8"
        allWarningsAsErrors = true
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            mavenCentralMetadata()
        }
    }
}