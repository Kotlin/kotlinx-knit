/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

rootProject.name = "kotlinx-knit"

apply(from = "buildSrc/repositories.settings.gradle.kts")


include(
    ":kotlinx-knit-core",
    ":kotlinx-knit-dokka",
    ":kotlinx-knit-gradle",
    ":kotlinx-knit-test",
)

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
enableFeaturePreview("STABLE_CONFIGURATION_CACHE")
