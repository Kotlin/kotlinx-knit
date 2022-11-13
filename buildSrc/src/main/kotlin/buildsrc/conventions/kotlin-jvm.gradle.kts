package buildsrc.conventions

import buildsrc.config.KotlinKnitBuildSettings
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.conventions.base")
    kotlin("jvm")
}

val knitBuildSettings = extensions.getByType<KotlinKnitBuildSettings>()

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = knitBuildSettings.jvmTarget.get()
//        allWarningsAsErrors = true
//        freeCompilerArgs = freeCompilerArgs + listOf("-Xsuppress-version-warnings") // suppress deprecated 1.4
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
