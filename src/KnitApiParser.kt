/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*
import java.util.HashMap

data class ApiIndexKey(
    val docsRoot: String,
    val pkg: String
)

class ApiIndexCache {
    val apiIndexCache = HashMap<ApiIndexKey, Map<String, List<String>>>()
}

const val INDEX_HTML = "/index.html"
const val INDEX_MD = "/index.md"
const val FUNCTIONS_SECTION_HEADER = "### Functions"
val REF_LINE_REGEX = Regex("<a href=\"([a-z0-9_/.\\-]+)\">([a-zA-z0-9.]+)</a>")

private fun HashMap<String, MutableList<String>>.putUnambiguous(key: String, value: String) {
    val oldValue = this[key]
    if (oldValue != null) {
        oldValue.add(value)
        put(key, oldValue)
    } else {
        put(key, mutableListOf(value))
    }
}

private fun loadApiIndex(
    rootDir: File,
    docsRoot: String,
    path: String,
    pkg: String,
    namePrefix: String = ""
): Map<String, MutableList<String>>? {
    val fileName = "$docsRoot/$path$INDEX_MD"
    val visited = mutableSetOf<String>()
    val map = HashMap<String, MutableList<String>>()
    var inFunctionsSection = false
    (rootDir / fileName).withLineNumberReader(::LineNumberReader) {
        while (true) {
            val line = readLine() ?: break
            if (line == FUNCTIONS_SECTION_HEADER) inFunctionsSection = true
            val result = REF_LINE_REGEX.matchEntire(line) ?: continue
            val link = result.groups[1]!!.value
            if (link.startsWith("..")) continue // ignore cross-references
            val absLink = "$path/$link"
            var name = result.groups[2]!!.value
            // a special disambiguation fix for pseudo-constructor functions
            if (inFunctionsSection && name[0] in 'A'..'Z') name += "()"
            val refName = namePrefix + name
            val fqName = "$pkg.$refName"
            // Put shorter names for extensions on 3rd party classes (prefix is FQname of those classes)
            if (namePrefix != "" && namePrefix[0] in 'a'..'z') {
                val i = namePrefix.dropLast(1).lastIndexOf('.')
                if (i >= 0) map.putUnambiguous(namePrefix.substring(i + 1) + name, absLink)
                map.putUnambiguous(name, absLink)
            }
            // Disambiguate lower-case names with leading underscore (e.g. Flow class vs flow builder ambiguity)
            if (namePrefix == "" && name[0] in 'a'..'z') {
                map.putUnambiguous("_$name", absLink)
            }
            // Always put fully qualified names
            map.putUnambiguous(refName, absLink)
            map.putUnambiguous(fqName, absLink)
            if (link.endsWith(INDEX_HTML)) {
                if (visited.add(link)) {
                    val path2 = path + "/" + link.substring(0, link.length - INDEX_HTML.length)
                    map += loadApiIndex(rootDir, docsRoot, path2, pkg, "$refName.")
                        ?: throw IllegalArgumentException("Failed to parse $docsRoot/$path2")
                }
            }
        }
    } ?: return null // return null on failure
    return map
}

fun ApiIndexCache.processApiIndex(
    siteRoot: String,
    rootDir: File,
    docsRoot: String,
    pkg: String,
    remainingApiRefNames: MutableSet<String>
): List<String>? {
    val key = ApiIndexKey(docsRoot, pkg)
    val map = apiIndexCache.getOrPut(key) {
        print("Parsing API docs at $docsRoot/$pkg: ")
        val result = loadApiIndex(rootDir, docsRoot, pkg, pkg) ?: return null // null on failure
        println("${result.size} definitions")
        result
    }
    val indexList = arrayListOf<String>()
    val it = remainingApiRefNames.iterator()
    while (it.hasNext()) {
        val refName = it.next()
        val refLink = map[refName] ?: continue
        // taking the shortest reference among candidates
        val link = refLink.minBy { it.length }
        indexList += "[$refName]: $siteRoot/$link"
        it.remove()
    }
    return indexList
}