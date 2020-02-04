/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.kotlin.gradle.plugin.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm") version "1.3.61"
    `maven-publish`
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(gradleApi())
    implementation("org.freemarker:freemarker:2.3.29")
    testImplementation(kotlin("test-junit"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

for ((name, dir) in mapOf("main" to "src", "test" to "test")) {
    sourceSets[name].withConvention(KotlinSourceSet::class) { kotlin.srcDirs(dir) }
}

sourceSets["main"].resources.srcDirs("resources")

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.apply {
        languageVersion = "1.3"
        jvmTarget = "1.8"
        allWarningsAsErrors = true
    }
}