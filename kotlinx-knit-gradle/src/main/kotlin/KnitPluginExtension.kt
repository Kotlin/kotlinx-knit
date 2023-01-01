package kotlinx.knit.gradle

import kotlinx.knit.*
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import java.io.File

abstract class KnitPluginExtension {
    abstract val knitVersion: Property<String>
    abstract val siteRoot: Property<String>
    abstract val moduleRoots: ListProperty<String>
    abstract val moduleMarkers: ListProperty<String>
    abstract val moduleDocs: Property<String>
    abstract val files: ConfigurableFileCollection
    abstract val rootDir: RegularFileProperty
    abstract val dokkaMultiModuleRoot: Property<String>
    abstract val defaultLineSeparator: Property<String>

    fun createContext(files: Collection<File>, rootDir: File, check: Boolean) = KnitContext(
        log = LoggerLog(),
        globals = KnitGlobals(
            siteRoot = siteRoot.get(),
            moduleRoots = moduleRoots.get(),
            moduleMarkers = moduleMarkers.get(),
            moduleDocs = moduleDocs.get(),
            dokkaMultiModuleRoot = dokkaMultiModuleRoot.get(),
        ),
        files = files,
        rootDir = rootDir,
        lineSeparator = evaluateLineSeparator(),
        check = check
    )

    private fun evaluateLineSeparator(): String {
        val unix = "\n"
        val windows = "\r\n"
        val ls = defaultLineSeparator.orNull
        if (ls != null && ls != unix && ls != windows) {
            throw GradleException(
                """
                    |Knit defaultLineSeparator must be one of:
                    |- Unix (\n)
                    |- Windows (\r\n)
                """.trimMargin()
            )
        }
        return ls ?: unix
    }
}
