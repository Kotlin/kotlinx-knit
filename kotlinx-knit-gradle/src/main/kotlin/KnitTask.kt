package kotlinx.knit.gradle

import kotlinx.knit.process
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.getByType


abstract class KnitTask : DefaultTask() {
    private val ext: KnitPluginExtension = project.extensions.getByType()

    @get:Input
    @get:Optional
    abstract val check: Property<Boolean>

    @get:Internal
    abstract val rootDir: RegularFileProperty

    @get:InputFiles
    abstract val files: ConfigurableFileCollection

    @TaskAction
    fun knit() {
        val rootDirFile = rootDir.asFile.get()
        val checkEnabled = check.getOrElse(false)

        val ctx = ext.createContext(files.files, rootDirFile, checkEnabled)

        if (!ctx.process() || checkEnabled && ctx.log.hasWarningOrError) {
            val extra = if (ctx.log.nOutdated > 0) {
                "\nRun 'knit' task to write ${ctx.log.nOutdated} missing/outdated files."
            } else {
                ""
            }
            throw GradleException("$name task failed, see log for details (use '--info' for detailed log).$extra")
        }
    }
}
