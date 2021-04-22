/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.knit.pathsaver.LINK_INDEX_FILE
import kotlinx.knit.pathsaver.LinkIndex
import org.jetbrains.dokka.links.*
import java.io.File
import java.io.FileNotFoundException
import java.io.LineNumberReader
import java.util.*

data class ApiIndexKey(
    val docsRoot: String,
    val pkg: String
)

private const val HTML_SUFFIX = ".html"
private const val MD_SUFFIX = ".md"

private const val INDEX_HTML = "/index$HTML_SUFFIX"
private const val INDEX_MD = "/index$MD_SUFFIX"

private val FUNCTIONS_SECTION_HEADER = Regex("(?:###|##|<h3>) (?:Functions|Extensions for [a-zA-Z0-9._]+)(?:</h3>)?")

private val REF_HTML_LINE_REGEX = Regex("(?:<h4>)?<a href=\"([a-z0-9_/.\\-]+)\">([a-zA-z0-9.]+)</a>(?:</h4>)?")
private val REF_MD_LINE_REGEX = Regex("\\| \\[([a-zA-z0-9.]+)]\\(([a-z0-9_/.\\-]+)\\) ?\\|.*")

// link ends with ".html"
private data class Ref(val link: String, val name: String)

private fun matchRef(line: String): Ref? {
    REF_HTML_LINE_REGEX.matchEntire(line)?.let {
        return Ref(link = it.groups[1]!!.value, name = it.groups[2]!!.value)
    }
    REF_MD_LINE_REGEX.matchEntire(line)?.let {
        var link = it.groups[2]!!.value
        if (link.endsWith(MD_SUFFIX)) link = link.substring(0, link.length - MD_SUFFIX.length) + HTML_SUFFIX
        return Ref(link = link, name = it.groups[1]!!.value)
    }
    return null
}

private fun String.checkFunctionSection(previousValue: Boolean): Boolean {
    // Direct match
    if (this.matches(FUNCTIONS_SECTION_HEADER)) return true
    // Proceed with previous value
    return previousValue
}

class ApiLink(val link: String, val isFunction: Boolean)

// always prefer classes to functions, then prefer shorter links
private val apiLinkComparator = compareBy(ApiLink::isFunction, { it.link.length })

class ApiIndex {
    private val m = HashMap<String, MutableList<ApiLink>>()
    val size: Int get() = m.size

    fun add(name: String, value: ApiLink) {
        // Do not contaminate existing links with relative ones
        val isRelative = value.link.contains("..")
        val list = m[name]
        if (list != null && isRelative) return
        if (list != null) {
            list.add(value)
        } else {
            m[name] = mutableListOf(value)
        }
    }

    fun addAll(other: ApiIndex) {
        for ((k, list) in other.m) {
            for (v in list) add(k, v)
        }
    }

    // taking the shortest reference among candidates, prefer classes to functions
    operator fun get(name: String): String? =
            m[name]?.minWith(apiLinkComparator)?.link
}

private fun KnitContext.parseIndexFiles(
        docsRoot: String,
        path: String,
        pkg: String,
        file: File,
        namePrefix: String = ""
): ApiIndex? {
    val visited = mutableSetOf<String>()
    val index = ApiIndex()
    var inFunctionsSection = false

    withLineNumberReader(file, ::LineNumberReader) {
        while (true) {
            val line = readLine() ?: break
            inFunctionsSection = line.checkFunctionSection(inFunctionsSection)
            val ref = matchRef(line) ?: continue
            val (link, name) = ref
            if (link.startsWith("..")) continue // ignore cross-references
            index.addName(pkg, name, path, link, inFunctionsSection, namePrefix)
            // Disambiguation with name() for functions
            if (inFunctionsSection) index.addName(pkg, "$name()", path, link, inFunctionsSection, namePrefix)
            // visit linked file if it is not a relative link
            if (link.endsWith(INDEX_HTML) && !link.startsWith("..")) {
                if (visited.add(link)) {
                    val path2 = path + "/" + link.substring(0, link.length - INDEX_HTML.length)
                    index.addAll(
                        loadApiIndex(docsRoot, path2, pkg, "$namePrefix$name.")
                            ?: throw IllegalArgumentException("Failed to parse $docsRoot/$path2")
                    )
                }
            }
        }
    } ?: return null // return null on failure
    return index
}

private fun parseLinkIndexFile(file: File, pkg: String): ApiIndex {
    val projectIndex = jacksonObjectMapper().readValue<List<LinkIndex>>(file)
    fun TypeReference.name(): String = when (this) {
        is TypeConstructor -> fullyQualifiedName
        is Nullable -> wrapped.name()
        else -> ""
    }

    return ApiIndex().apply {
        projectIndex
            .filter { it.dri.packageName?.equals(pkg, ignoreCase = true) == true }
            .forEach { entry ->
                val pkgName = entry.dri.packageName.orEmpty()
                val isFunction = entry.dri.mayBeAFunction()
                val classNames = entry.dri.classNames
                val callable = entry.dri.callable
                val receiverName = callable?.receiver?.name()?.takeIf(String::isNotBlank)?.let { "$it." }.orEmpty()
                val path = entry.location.substringBefore("/")
                val location = entry.location.substringAfter("/")

                if (callable != null) {
                    addName(pkgName, callable.name, path, location, isFunction, "")
                    if (isFunction)
                        addName(pkgName, callable.name + "()", path, location, isFunction, "")
                    if (classNames == null) {
                        addName(pkgName, callable.name, path, location, isFunction, receiverName)
                        if (isFunction)
                            addName(pkgName, callable.name + "()", path, location, isFunction, receiverName)
                    } else {
                        addName(pkgName, callable.name, path, location, isFunction, "$classNames.")
                        if (isFunction)
                            addName(pkgName, callable.name + "()", path, location, isFunction, "$classNames.")
                    }
                } else {
                    if (classNames != null) {
                        addName(pkgName, classNames, path, location, isFunction, "")
                    }
                }
            }
    }
}

private fun DRI.mayBeAFunction() = callable != null

private fun ApiIndex.addName(pkg: String, name: String, path: String, link: String, isFunction: Boolean, namePrefix: String) {
    val apiLink = ApiLink("$path/$link", isFunction)
    val refName = namePrefix + name
    val fqName = "$pkg.$refName"
    // Put shorter names for extensions on 3rd party classes (prefix is FQname of those classes)
    if (namePrefix != "" && namePrefix[0] in 'a'..'z') {
        val i = namePrefix.dropLast(1).lastIndexOf('.')
        if (i >= 0) add(namePrefix.substring(i + 1) + name, apiLink)
        add(name, apiLink)
    }
    // Additionally disambiguate lower-case names with leading underscore.
    // This is helpful when there are two classes or functions that differ only in case of the first letter.
    // Note, that a class can be disambiguated from a function with trailing () for function.
    if (namePrefix == "" && name[0] in 'a'..'z') {
        add("_$name", apiLink)
    }
    // Always put fully qualified names
    add(refName, apiLink)
    add(fqName, apiLink)
}

private fun KnitContext.loadApiIndex(
        docsRoot: String,
        path: String,
        indexDirective: String,
        moduleName: String,
        namePrefix: String = ""
): ApiIndex? {
    val pkg = indexDirective.substringAfter("/")
    val fileDir = rootDir / docsRoot / path

    return findFileOrNull((rootDir / docsRoot).parentFile, LINK_INDEX_FILE)?.let {
        parseLinkIndexFile(it, pkg)
    } ?: parseIndexFiles(docsRoot, "$moduleName/$path", pkg, findFile(fileDir, INDEX_MD, INDEX_HTML), namePrefix)
}

fun findFile(fileDir: File, vararg names: String): File =
        names.map { fileDir / it }.firstOrNull { it.exists() }
                ?: throw FileNotFoundException("Cannot find one of files ${names.joinToString(", ") { "'$it'" }} in $fileDir")

fun findFileOrNull(fileDir: File, vararg names: String): File? = runCatching { findFile(fileDir, *names) }.getOrNull()

fun KnitContext.processApiIndex(
    inputFile: File,
    siteRoot: String,
    moduleName: String,
    docsRoot: String,
    pkg: String,
    remainingApiRefNames: MutableSet<String>,
    uppercaseApiRefNames: HashMap<String, String>
): List<String>? {
    val key = ApiIndexKey(docsRoot, pkg)
    val index = apiIndexCache.getOrPut(key) {
        val result = loadApiIndex(docsRoot, pkg, pkg, moduleName) ?: return null // null on failure
        log.debug("Parsed API docs at $docsRoot/$pkg: ${result.size} definitions")
        result
    }
    val indexList = arrayListOf<String>()
    val it = remainingApiRefNames.iterator()
    while (it.hasNext()) {
        val refName = it.next()
        val link = index[refName] ?: continue
        val siteLink = "$siteRoot/$link"
        indexList += "[$refName]: $siteLink"
        it.remove()
        val oldNameCase = uppercaseApiRefNames.put(refName.toUpperCase(Locale.ROOT), refName)
        if (oldNameCase != null) {
            log.warn("WARNING: $inputFile: References [$refName] and [$oldNameCase] are different only in case, not distinguishable in markdown.")
        }
    }
    return indexList
}
