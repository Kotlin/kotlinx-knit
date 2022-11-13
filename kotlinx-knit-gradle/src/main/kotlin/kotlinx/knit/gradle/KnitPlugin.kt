/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit.gradle

import kotlinx.knit.globalDefaults
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.logging.Logging
import org.gradle.api.model.ObjectFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.base.plugins.LifecycleBasePlugin
import javax.inject.Inject


abstract class KnitPlugin @Inject constructor(
    private val objects: ObjectFactory,
) : Plugin<Project> {

    private val logger = Logging.getLogger(KnitPlugin::class.java)

    override fun apply(project: Project) {
        val extension = project.createExtension()

        val knitPrepare = project.tasks.register<DefaultTask>("knitPrepare") {
            description = "Prepares dependencies for Knit tool"
            group = TASK_GROUP
        }

        project.tasks.withType<KnitTask>().configureEach {
            group = TASK_GROUP
            check.convention(false)
            rootDir.convention(extension.rootDir)
            files.from(extension.files)
        }

        val knitCheck = project.tasks.register<KnitTask>(TASK_NAME_KNIT_CHECK) {
            description = "Runs Knit tool check (does not modify anything)"
            check.convention(true)
            dependsOn(knitPrepare)
        }

        project.tasks.register<KnitTask>(TASK_NAME_KNIT) {
            description = "Runs Knit tool"
            dependsOn(knitPrepare)
        }

        project.plugins.withType<LifecycleBasePlugin> {
            project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME).configure {
                dependsOn(knitCheck)
            }
        }

        project.configurations.register("knitRuntimeClasspath") {
            isCanBeResolved = true
            isCanBeConsumed = false
            isVisible = false

            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            }

            defaultDependencies {
                project.dependencies.create(
                    extension.knitVersion.map { ver -> "org.jetbrains.kotlinx:kotlinx-knit-test:$ver" }
                )
            }
        }

        // Configure default version resolution for 'kotlinx-knit-test'

//        val pluginVersion = project.rootProject.buildscript.configurations.findByName("classpath")
//            ?.allDependencies?.find { it.group == DEPENDENCY_GROUP && it.name == "kotlinx-knit" }?.version
//
//        logger.debug("Knit plugin version: $pluginVersion")
//
//        if (pluginVersion != null) {
//            project.configurations.all {
//                resolutionStrategy.eachDependency {
//                    if (requested.group == DEPENDENCY_GROUP && requested.name == "kotlinx-knit-test" && requested.version == null) {
//                        useVersion(pluginVersion)
//                    }
//                }
//            }
//        }
    }

    private fun Project.createExtension(): KnitPluginExtension {
        return extensions.create(EXTENSION_NAME, KnitPluginExtension::class).apply {
            siteRoot.convention(globalDefaults.siteRoot)
            moduleRoots.convention(globalDefaults.moduleRoots)
            moduleMarkers.convention(globalDefaults.moduleMarkers)
            moduleDocs.convention(globalDefaults.moduleDocs)
            dokkaMultiModuleRoot.convention(globalDefaults.dokkaMultiModuleRoot)
            rootDir.convention(layout.projectDirectory.file("."))
            files.from(layout.projectDirectory.asFileTree.matching {
                include("**/*.md")
                include("**/*.kt")
                include("**/*.kts")
                exclude("**/build/*", "**/.gradle/*")
            })
        }
    }

    private fun Project.configureKnitTasks() {

    }

    companion object {
        const val TASK_GROUP = "documentation"
        const val DEPENDENCY_GROUP = "org.jetbrains.kotlinx"
        const val EXTENSION_NAME = "knit"
        const val TASK_NAME_KNIT = "knit"
        const val TASK_NAME_KNIT_CHECK = "knit"
    }
}
