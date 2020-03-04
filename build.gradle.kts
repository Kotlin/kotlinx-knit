/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import com.gradle.publish.*
import kotlinx.knit.build.*
import org.jetbrains.kotlin.gradle.tasks.*

plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka") apply false
    id("com.gradle.plugin-publish") apply false
    `java-gradle-plugin`
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
    
    tasks.withType<KotlinCompile> {
        kotlinOptions.apply {
            languageVersion = "1.3"
            jvmTarget = "1.6"
            allWarningsAsErrors = true
        }
    }

    sourceSets {
        main.kotlin.dir = "src"
        test.kotlin.dir = "test"
        main.resources.dir = "resources"
    }

    // Set version when deploying
    properties["DeployVersion"]?.let { version = it }
    
    publishing {
        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
                mavenCentralArtifacts(project, project.sourceSets.main.allSource)
            }
        }
        mavenCentralMetadata()
        bintrayRepositoryPublishing(project, user = "kotlin", repo = "kotlinx", name = "kotlinx.knit")
    }
}

// ---------- This root project -- Gradle plugin ----------

apply(plugin = "org.gradle.java-gradle-plugin")
apply(plugin = "com.gradle.plugin-publish")

extensions.getByType(PluginBundleExtension::class).apply {
    website = "https://github.com/Kotlin/kotlinx-knit"
    vcsUrl = "https://github.com/Kotlin/kotlinx-knit"
    tags = listOf("kotlin", "documentation", "markdown")
}

gradlePlugin {
    plugins {
        create("kotlinx-knit") {
            // This is a fully-qualified plugin id, short id of 'kotlinx-knit' is added manually in resources
            id = "org.jetbrains.kotlinx.knit"
            implementationClass = "kotlinx.knit.KnitPlugin"
            displayName = "Knit documentation plugin"
            description = "Produces Kotlin source example files and tests from markdown documents with embedded snippets of Kotlin code"
        }
    }
}

val publishPlugins by tasks.getting(PublishTask::class)

val deploy: Task by tasks.creating {
    dependsOn(getTasksByName("publish", true))
    dependsOn(publishPlugins)
}

val freemarkerVersion: String by project

dependencies {
    implementation(gradleApi())
    implementation("org.freemarker:freemarker:$freemarkerVersion")
    implementation(project(":kotlinx-knit-test"))
}

val test: Task by tasks.getting {
    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokka"))
}
