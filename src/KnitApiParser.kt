/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*
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
private val FUNCTION_SECTION_HEADER_NEW_DOKKA = Regex(".*<div class=\"table\" data-togglable=\"([A-Za-z]+)\">.*")

private val REF_HTML_LINE_REGEX = Regex("(?:<h4>)?<a href=\"([a-z0-9_/.\\-]+)\">([a-zA-z0-9.]+)</a>(?:</h4>)?")
private val REF_MD_LINE_REGEX = Regex("\\| \\[([a-zA-z0-9.]+)]\\(([a-z0-9_/.\\-]+)\\) ?\\|.*")
/*
 * In new Dokka, output HTML is formatted and has levels of nesting, so we are matching wildcards here.
 * Line with an actual link looks like this:
 * <div class="main-subrow "><span><a href="build-json-array.html">buildJsonArray</a><span class="anchor-wrapper"><span class="anchor-icon" pointing-to="kotlinx.serialization.json//buildJsonArray/#kotlin.Function1[kotlinx.serialization.json.JsonArrayBuilder,kotlin.Unit]/PointingToDeclaration/"><svg width="24" height="24" viewbox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
 *
 * And, for extensions, like this (trimmed):
 * <a href="index.html#kotlinx.serialization.json%2FJsonBuilder%2FallowSpecialFloatingPointValues%2F%23%2FPointingToDeclaration%2F">
 */
private val REF_HTML_NEW_DOKKA_LINE_REGEX = Regex(".*<div class=\"main-subrow \"><span><a href=\"((index.html#)?([a-zA-Z0-9%#_/.\\-]+))\">([a-zA-z0-9.]+)</a>.*")
/*
 * This particular regex is required to extract pointer to top-level function that has multiple overloads.
 * For some reason, such functions have relative links and the pointer is contained in a separate HTML tag.
 * You can examine the source here: https://kotlin.github.io/kotlinx.serialization/kotlinx-serialization-core/kotlinx-serialization-core/kotlinx.serialization/index.html#kotlinx.serialization//serializer/#/PointingToDeclaration/
 */
private val REF_HTML_NEW_DOKKA_POINTER_REGEX = Regex(".*<span class=\"anchor-icon\" pointing-to=\"([a-zA-Z0-9%#_/.\\-]+)\">.*")

// link ends with ".html"
private data class Ref(val link: String, val name: String, val newDokka: Boolean = false)

private fun matchRef(line: String): Ref? {
    REF_HTML_LINE_REGEX.matchEntire(line)?.let {
        return Ref(link = it.groups[1]!!.value, name = it.groups[2]!!.value)
    }
    REF_HTML_NEW_DOKKA_LINE_REGEX.matchEntire(line)?.let {
        var link = it.groups[1]!!.value
        val name = it.groups[4]!!.value
        if (link.startsWith("..")) {
            val pagePointer = REF_HTML_NEW_DOKKA_POINTER_REGEX.matchEntire(line) ?: return null
            val value = pagePointer.groups[1] ?: return null
            link += "#${value.value}"
        }
        return Ref(link = link, name = name, newDokka = true)
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
    // New section -- reset previous value. Applicable only to new Dokka
    FUNCTION_SECTION_HEADER_NEW_DOKKA.matchEntire(this)?.let {
        val group = it.groups[1]!!.value
        return group == "Functions"
    }
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

private fun KnitContext.loadApiIndex(
    docsRoot: String,
    path: String,
    indexDirective: String,
    namePrefix: String = ""
): ApiIndex? {
    // For new Dokka "/" is actively used in index directive
    val pkg = indexDirective.substringAfter("/")
    val fileDir = rootDir / docsRoot / path
    val file = findFile(fileDir, INDEX_MD, INDEX_HTML)
    val visited = mutableSetOf<String>()
    val index = ApiIndex()
    var inFunctionsSection = false
    fun addName(name: String, link: String, isFunction: Boolean) {
        val apiLink = ApiLink("$path/$link", isFunction)
        val refName = namePrefix + name
        val fqName = "$pkg.$refName"
        // Put shorter names for extensions on 3rd party classes (prefix is FQname of those classes)
        if (namePrefix != "" && namePrefix[0] in 'a'..'z') {
            val i = namePrefix.dropLast(1).lastIndexOf('.')
            if (i >= 0) index.add(namePrefix.substring(i + 1) + name, apiLink)
            index.add(name, apiLink)
        }
        // Additionally disambiguate lower-case names with leading underscore.
        // This is helpful when there are two classes or functions that differ only in case of the first letter.
        // Note, that a class can be disambiguated from a function with trailing () for function.
        if (namePrefix == "" && name[0] in 'a'..'z') {
            index.add("_$name", apiLink)
        }
        // Always put fully qualified names
        index.add(refName, apiLink)
        index.add(fqName, apiLink)
    }
    withLineNumberReader(file, ::LineNumberReader) {
        while (true) {
            val line = readLine() ?: break
            inFunctionsSection = line.checkFunctionSection(inFunctionsSection)
            val ref = matchRef(line) ?: continue
            val (link, name) = ref
            /**
             * For new Dokka we process some of the relative links because
             * they are the only reference to some declarations.
             * See comment to REF_HTML_NEW_DOKKA_POINTER_REGEX.
             */
            if (link.startsWith("..") && !ref.newDokka) continue // ignore cross-references
            addName(name, link, inFunctionsSection)
            // Disambiguation with name() for functions
            if (inFunctionsSection) addName("$name()", link, inFunctionsSection)
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

fun findFile(fileDir: File, vararg names: String): File =
    names.map { fileDir / it }.firstOrNull { it.exists() } ?:
        throw FileNotFoundException("Cannot find one of files ${names.joinToString(", ") { "'$it'" } } in $fileDir")

fun KnitContext.processApiIndex(
    inputFile: File,
    siteRoot: String,
    docsRoot: String,
    pkg: String,
    remainingApiRefNames: MutableSet<String>,
    uppercaseApiRefNames: HashMap<String, String>
): List<String>? {
    val key = ApiIndexKey(docsRoot, pkg)
    val index = apiIndexCache.getOrPut(key) {
        val result = loadApiIndex(docsRoot, pkg, pkg) ?: return null // null on failure
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
