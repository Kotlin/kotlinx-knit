/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

import com.gradle.publish.*
import kotlinx.knit.build.*

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish")
    signing
    `maven-publish`
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            // Don't setup empty sources and javadoc jars for the plugin publication,
            // plugin-publish plugin will do that on its own.
            // mavenCentralArtifacts(project, project.sourceSets.main.allSource)
        }
    }
    mavenCentralMetadata()
    mavenRepositoryPublishing(project)
    publications.withType(MavenPublication::class).all {
        signPublicationIfKeyPresent(this)
    }
}

extensions.getByType(PluginBundleExtension::class).apply {
    website = "https://github.com/Kotlin/kotlinx-knit"
    vcsUrl = "https://github.com/Kotlin/kotlinx-knit"
    tags = listOf("kotlin", "documentation", "markdown")
}

signing {
    // disable signing if a private key isn't passed
    isRequired = findProperty("libs.sign.key.private") != null
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
    doFirst {
        error(":deploy task is no longer works. " +
                "To publish the plugin create and upload a deployment bundle by running " +
                ":publishAllPublicationsToBuildRepoRepository and the archiving the contents of build/repo. " +
                "To publish a plugin to the Gradle portal, run :publishPlugins task.")
    }
}

val freemarkerVersion: String by project
val dokkaVersion: String by project

dependencies {
    implementation(gradleApi())
    implementation(project(":pathsaver"))
    implementation("org.freemarker:freemarker:$freemarkerVersion")
    implementation(project(":kotlinx-knit-test"))
    implementation("org.jetbrains.dokka:dokka-core:$dokkaVersion")
}

val test: Task by tasks.getting {
    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokka"))
    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokkaHtml"))
}
