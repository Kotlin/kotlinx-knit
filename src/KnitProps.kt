/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import freemarker.template.*
import java.io.*
import java.util.*

const val KNIT_PROPERTIES = "knit.properties"

val globalProperties = KnitProps() // from bundled resources
val globalDefaults = KnitGlobals(globalProperties)

fun createDefaultContext(
    files: List<File>,
    globalPropsDir: File? = null
): KnitContext {
    var globals: KnitGlobals = globalDefaults
    if (globalPropsDir != null && File(globalPropsDir, KNIT_PROPERTIES).exists()) {
        globals = KnitGlobals(KnitProps(DirectoryProps(globalPropsDir), globalProperties))
    }
    return KnitContext(
        log = ConsoleLog(),
        globals = globals,
        files = files,
        rootDir = File(System.getProperty("user.dir")),
        lineSeparator = System.lineSeparator(),
        check = false
    )
}

class KnitProps(
    private val location: PropsLocation = ResourcesProps,
    private val parent: KnitProps? = null
) {
    private val props: Properties = Properties().apply {
        location.open(KNIT_PROPERTIES).use { load(it) }
        putAll(System.getProperties()) // Support overwrite of built-in resources with system properties
    }

    operator fun get(name: String): String? =
        props.getProperty(name) ?: parent?.get(name)

    fun getValue(name: String): String =
        get(name) ?: error("Missing property '$name' in $location")

    fun getFile(name: String): File =
        getFileImpl(name) ?: error("Missing property '$name' in $location")

    private fun getFileImpl(name: String): File? =
        props.getProperty(name)?.let { location.resolveFile(it) } ?: parent?.getFileImpl(name)

    fun getMap(prefix: String, vararg plus: Pair<String, String>): Map<String, String> {
        val result = HashMap<String, String>()
        parent?.getMap(prefix)?.let { result.putAll(it) }
        val p = "$prefix."
        @Suppress("UNCHECKED_CAST")
        for ((k, v) in (props as Map<String, String>)) {
            if (k.startsWith(p)) result[k.substring(p.length)] = v
        }
        for ((k, v) in plus) {
            result[k] = v
        }
        return result
    }

    fun loadTemplateLines(name: String, env: Any): List<String> =
        loadTemplateLinesImpl(name, env) ?: error("Missing property '$name' in $location")

    private fun loadTemplateLinesImpl(name: String, env: Any): List<String>? {
        val value = props.getProperty(name) ?: return parent?.loadTemplateLinesImpl(name, env)
        val template = location.templateConfig.getTemplate(value)
        val writer = StringWriter()
        template.process(env, writer)
        // return all lines and remove all extra blank lines
        return StringReader(writer.toString()).readLines().dropLastWhile { it.isBlank() }
    }
}

fun KnitContext.findProps(file: File, rootDir: File): KnitProps =
    findDirProps(file.absoluteFile.parentFile, rootDir.absolutePath)

private fun KnitContext.findDirProps(dir: File?, rootPath: String): KnitProps {
    if (dir == null || !dir.path.startsWith(rootPath)) return globalProperties
    propsCache[dir]?.let { return it }
    val propFile = File(dir, KNIT_PROPERTIES)
    if (!propFile.exists()) return findDirProps(dir.parentFile, rootPath)
    val parent = findDirProps(dir.parentFile, rootPath)
    log.debug("Loading properties from $propFile")
    val props = KnitProps(DirectoryProps(dir), parent)
    propsCache[dir] = props
    return props
}

abstract class PropsLocation {
    val templateConfig by lazy {
        Configuration(Configuration.VERSION_2_3_29).apply {
            defaultEncoding = "UTF-8"
            templateExceptionHandler = TemplateExceptionHandler.RETHROW_HANDLER
            logTemplateExceptions = false
            wrapUncheckedExceptions = true
            fallbackOnNullLoopVariable = false
            initTemplate()
        }
    }

    abstract fun Configuration.initTemplate()
    abstract fun open(name: String): InputStream
    abstract fun resolveFile(name: String): File
}

private object ResourcesProps : PropsLocation() {
    private val classLoader: ClassLoader = ResourcesProps::class.java.classLoader

    override fun Configuration.initTemplate() {
        setClassLoaderForTemplateLoading(classLoader, "")
    }
    
    override fun open(name: String): InputStream =
        classLoader.getResourceAsStream(name) ?: cannotFind(name)

    private fun cannotFind(name: String): Nothing {
        throw FileNotFoundException("Cannot find resource: $name")
    }

    override fun resolveFile(name: String): File =
        error("Cannot specify '$name' property in resources")

    override fun toString(): String = "resources"
}

private class DirectoryProps(private val dir: File) : PropsLocation() {
    override fun Configuration.initTemplate() {
        setDirectoryForTemplateLoading(dir)
    }
    
    override fun open(name: String): InputStream =
        File(dir, name.replace('/', File.separatorChar)).inputStream()

    override fun resolveFile(name: String): File = File(dir, name)

    override fun toString(): String = File(dir, KNIT_PROPERTIES).path
}
