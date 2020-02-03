/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import java.io.*

class KnitPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        extensions.create("knit", KnitPluginExtension::class.java)
        tasks.register("knit", KnitTask::class.java)
    }
}

open class KnitTask : DefaultTask() {
    init {
        description = "Runs knit tool"
    }

    private val ext: KnitPluginExtension = project.extensions.getByType(KnitPluginExtension::class.java)

    @Input
    var rootDir: File = ext.rootDir ?: project.rootDir

    @InputFiles
    var files: FileCollection = ext.files ?: project.files("*.md")

    @TaskAction
    fun knit() {
        val ctx = ext.createContext(files.files, rootDir)
        if (!ctx.process()) throw GradleException("Knit failed, see log for details")
    }
}

open class KnitPluginExtension {
    var siteRoot: String? = null
    var moduleRoots: List<String> = listOf(".")
    var moduleMarkers: List<String> = listOf("build.gradle", "build.gradle.kts")
    var moduleDocs: String = "build/dokka"
    var files: FileCollection? = null
    var rootDir: File? = null

    fun createContext(files: Collection<File>, rootDir: File) = KnitContext(
        siteRoot = siteRoot,
        moduleRoots = moduleRoots,
        moduleMarkers = moduleMarkers,
        moduleDocs =  moduleDocs,
        files = files,
        rootDir = rootDir
    )
}