/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

plugins {
    buildsrc.conventions.base
}

group = "kotlinx.knit"
version = "0.4.0-SNAPSHOT"


//
//publishing {
//    publications {
//        create<MavenPublication>("maven") {
//            from(components["java"])
//            mavenCentralArtifacts(project, project.sourceSets.main.allSource)
//        }
//    }
//    mavenCentralMetadata()
//    mavenRepositoryPublishing(project)
//    publications.withType(MavenPublication::class).all {
//        signPublicationIfKeyPresent(this)
//    }
//}
//
//// ---------- This root project -- Gradle plugin ----------
//
//apply(plugin = "org.gradle.java-gradle-plugin")
//apply(plugin = "com.gradle.plugin-publish")
//
//extensions.getByType(PluginBundleExtension::class).apply {
//    website = "https://github.com/Kotlin/kotlinx-knit"
//    vcsUrl = "https://github.com/Kotlin/kotlinx-knit"
//    tags = listOf("kotlin", "documentation", "markdown")
//}
//
//gradlePlugin {
//    plugins {
//        create("kotlinx-knit") {
//            // This is a fully-qualified plugin id, short id of 'kotlinx-knit' is added manually in resources
//            id = "org.jetbrains.kotlinx.knit"
//            implementationClass = "kotlinx.knit.KnitPlugin"
//            displayName = "Knit documentation plugin"
//            description = "Produces Kotlin source example files and tests from markdown documents with embedded snippets of Kotlin code"
//        }
//    }
//}
//
//val publishPlugins by tasks.getting(PublishTask::class)
//
//val deploy: Task by tasks.creating {
//    dependsOn(getTasksByName("publish", true))
//    dependsOn(publishPlugins)
//}
//
//val freemarkerVersion: String by project
//val dokkaVersion: String by project
//
//dependencies {
//    implementation(gradleApi())
//    implementation(project(":pathsaver"))
//    implementation("org.freemarker:freemarker:$freemarkerVersion")
//    implementation(project(":kotlinx-knit-test"))
//    implementation("org.jetbrains.dokka:dokka-core:$dokkaVersion")
//}
//
//val test: Task by tasks.getting {
//    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokka"))
//    dependsOn(tasks.findByPath(":kotlinx-knit-test:dokkaHtml"))
//}
