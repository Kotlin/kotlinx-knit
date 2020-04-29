/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

rootProject.name = "kotlinx-knit"
include("kotlinx-knit-test")

pluginManagement {
    plugins {
        val kotlinVersion: String by extra
        val dokkaVersion: String by extra
        val pluginPublishVersion: String by extra

        kotlin("jvm") version kotlinVersion
        id("org.jetbrains.dokka") version dokkaVersion
        id("com.gradle.plugin-publish") version pluginPublishVersion
    }
}
