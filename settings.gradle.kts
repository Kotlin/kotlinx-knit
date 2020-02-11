/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

rootProject.name = "kotlinx-knit"
include("kotlinx-knit-test")

val kotlinVersion: String by settings
val dokkaVersion: String by settings
val pluginPublishVersion: String by settings

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            val id = requested.id.id
            when {
                id.startsWith("org.jetbrains.kotlin.") -> useVersion(kotlinVersion)
                id == "org.jetbrains.dokka" -> useVersion(dokkaVersion)
                id == "com.gradle.plugin-publish" -> useVersion(pluginPublishVersion)
            }
        }
    }
}