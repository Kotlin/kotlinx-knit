package buildsrc.conventions

import buildsrc.config.KotlinKnitBuildSettings

plugins {
    base
}

if (project != rootProject) {
    project.version = rootProject.version
    project.group = rootProject.group
}


extensions.create(KotlinKnitBuildSettings.EXTENSION_NAME, KotlinKnitBuildSettings::class)


tasks.withType<AbstractArchiveTask>().configureEach {
    // https://docs.gradle.org/current/userguide/working_with_files.html#sec:reproducible_archives
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
