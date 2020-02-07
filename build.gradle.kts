/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.knit.build.*
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka") apply false
    `maven-publish`
}

allprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.gradle.maven-publish")

    repositories {
        jcenter()
    }

    dependencies {
        implementation(kotlin("stdlib-jdk8"))
        testImplementation(kotlin("test-junit"))
    }
    
    tasks.withType<KotlinCompile>().configureEach {
        kotlinOptions.apply {
            languageVersion = "1.3"
            jvmTarget = "1.8"
            allWarningsAsErrors = true
        }
    }

    sourceSets {
        main.kotlin.dir = "src"
        test.kotlin.dir = "test"
        main.resources.dir = "resources"
    }

    val sourcesJar by tasks.creating(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets.main.allSource)
    }

    val javadocJar by tasks.creating(Jar::class) {
        archiveClassifier.set("javadoc")
    }

    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                artifact(sourcesJar)
                artifact(javadocJar)
                mavenCentralMetadata()
            }
        }

        bintrayRepositoryPublishing(project, user = "kotlin", repo = "kotlinx", name = "kotlinx.knit")
    }
}

// ---------- This root project -- Gradle plugin ----------

val freemarkerVersion: String by project

dependencies {
    implementation(gradleApi())
    implementation("org.freemarker:freemarker:$freemarkerVersion")
    implementation(project(":kotlinx-knit-test"))
}

val test by tasks.getting(AbstractTestTask::class) {
    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokka"))
}
