/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.apache.log4j.*
import org.gradle.api.*
import org.gradle.api.file.*
import org.gradle.api.tasks.*
import java.io.*

const val TASK_GROUP = "documentation"
const val DEPENDENCY_GROUP = "org.jetbrains.kotlinx"

class KnitPlugin : Plugin<Project> {
    override fun apply(project: Project): Unit = with(project) {
        // Create tasks
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
        tasks.named("check").configure {
            it.dependsOn(knitCheck)
        }
        // Configure default version resolution for 'kotlinx-knit-test'
        val pluginVersion = rootProject.buildscript.configurations.findByName("classpath")
            ?.allDependencies?.find { it.group == DEPENDENCY_GROUP && it.name == "kotlinx-knit" }?.version
        Logger.getLogger(KnitPlugin::class.java).debug("Plugin version: $pluginVersion")
        if (pluginVersion != null) {
            configurations.all { configuration ->
                configuration.resolutionStrategy.eachDependency { dependency ->
                    val requested = dependency.requested
                    if (requested.group == DEPENDENCY_GROUP && requested.name == "kotlinx-knit-test" && requested.version == null) {
                        dependency.useVersion(pluginVersion)
                    }
                }
            }
        }
    }
}

open class KnitTask : DefaultTask() {
    private val ext: KnitPluginExtension = project.extensions.getByType(KnitPluginExtension::class.java)

    @Input
    var check: Boolean = false

    @InputDirectory
    var rootDir: File = ext.rootDir ?: project.rootDir

    @InputFiles
    var files: FileCollection = ext.files ?: project.fileTree(project.rootDir) {
        it.include("**/*.md")
        it.include("**/*.kt")
        it.include("**/*.kts")
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
    var siteRoot: String? = globalDefaults.siteRoot
    var moduleRoots: List<String> = globalDefaults.moduleRoots
    var moduleMarkers: List<String> = globalDefaults.moduleMarkers
    var moduleDocs: String = globalDefaults.moduleDocs
    var files: FileCollection? = null
    var rootDir: File? = null

    fun createContext(files: Collection<File>, rootDir: File, check: Boolean) = KnitContext(
        log = LoggerLog(),
        globals = KnitGlobals(
            siteRoot = siteRoot,
            moduleRoots = moduleRoots,
            moduleMarkers = moduleMarkers,
            moduleDocs =  moduleDocs
        ),
        files = files,
        rootDir = rootDir,
        check = check
    )
}
