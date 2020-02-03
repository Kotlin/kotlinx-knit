/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import freemarker.template.*
import java.io.*
import java.util.*

const val KNIT_PROPERTIES = "knit.properties"

private val globalProperties = KnitProps()

fun createDefaultContext(files: List<File>): KnitContext =
    with(globalProperties) {
        KnitContext(
            siteRoot = getValue("site.root"),
            moduleRoots = getValue("module.roots").split(","),
            moduleMarkers = getValue("module.markers").split(","),
            moduleDocs = getValue("module.docs"),
            files = files,
            rootDir = File(System.getProperty("user.dir"))
        )
    }

class KnitProps(
    private val location: PropsLocation = ResourcesProps,
    private val parent: KnitProps? = null
) {
    private val props: Properties = location.open(KNIT_PROPERTIES).use { Properties().apply { load(it) } }

    operator fun get(name: String): String? =
        props.getProperty(name) ?: parent?.get(name)

    fun getValue(name: String): String =
        get(name) ?: error("Missing property '$name' in $location")

    fun getFile(name: String): File =
        getFileImpl(name) ?: error("Missing property '$name' in $location")

    private fun getFileImpl(name: String): File? =
        props.getProperty(name)?.let { location.resolveFile(it) } ?: parent?.getFileImpl(name)

    fun getMap(prefix: String): Map<String, String> {
        val result = HashMap<String, String>()
        parent?.getMap(prefix)?.let { result.putAll(it) }
        val p = "$prefix."
        @Suppress("UNCHECKED_CAST")
        for ((k, v) in (props as Map<String, String>)) {
            if (k.startsWith(p)) result[k.substring(p.length)] = v
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

fun File.findProps(rootDir: File): KnitProps =
    absoluteFile.parentFile?.findDirProps(rootDir.absolutePath) ?: globalProperties

private val propsCache = HashMap<File, KnitProps>()

private fun File.findDirProps(rootPath: String): KnitProps {
    if (!path.startsWith(rootPath)) return globalProperties
    propsCache[this]?.let { return it }
    val propFile = File(this, KNIT_PROPERTIES)
    if (!propFile.exists()) return parentFile.findDirProps(rootPath)
    val props = KnitProps(DirectoryProps(this), parentFile?.findDirProps(rootPath))
    propsCache[this] = props
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