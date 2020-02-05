/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import java.io.*

const val TASK_GROUP = "documentation"

class KnitPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        extensions.create("knit", KnitPluginExtension::class.java)
        val knitPrepare = tasks.register("knitPrepare", DefaultTask::class.java) {
            it.description =  "Prepares dependencies for Knit tool"
            it.group = TASK_GROUP
        }
        val knitCheck = tasks.register("knitCheck", KnitTask::class.java) {
            it.description = "Runs Knit tool check (does not modify anything)"
            it.group = TASK_GROUP
            it.check = true
            it.dependsOn(knitPrepare)
        }
        tasks.register("knit", KnitTask::class.java) {
            it.description = "Runs Knit tool"
            it.group = TASK_GROUP
            it.dependsOn(knitPrepare)
        }
        tasks.register("check") {
            it.dependsOn(knitCheck)
        }
    }
}

open class KnitTask : DefaultTask() {
    private val ext: KnitPluginExtension = project.extensions.getByType(KnitPluginExtension::class.java)

    @Input
    var check: Boolean = false

    @Input
    var rootDir: File = ext.rootDir ?: project.rootDir

    @InputFiles
    var files: FileCollection = ext.files ?: project.fileTree(project.rootDir) {
        it.include("**/*.md")
        it.exclude("**/build/*")
        it.exclude("**/.gradle/*")
    }

    @TaskAction
    fun knit() {
        val ctx = ext.createContext(files.files, rootDir, check)
        if (!ctx.process() || check && ctx.log.hasWarningOrError) {
            val extra = if (ctx.log.nOutdated > 0)
                "\nRun 'knit' task to write ${ctx.log.nOutdated} missing/outdated files."
            else
                ""
            throw GradleException("$name task failed, see log for details (use '--info' for detailed log).$extra")
        }
    }
}

open class KnitPluginExtension {
    var siteRoot: String? = null
    var moduleRoots: List<String> = listOf(".")
    var moduleMarkers: List<String> = listOf("build.gradle", "build.gradle.kts")
    var moduleDocs: String = "build/dokka"
    var files: FileCollection? = null
    var rootDir: File? = null

    fun createContext(files: Collection<File>, rootDir: File, check: Boolean) = KnitContext(
        log = LoggerLog(),
        siteRoot = siteRoot,
        moduleRoots = moduleRoots,
        moduleMarkers = moduleMarkers,
        moduleDocs =  moduleDocs,
        files = files,
        rootDir = rootDir,
        check = check
    )
}