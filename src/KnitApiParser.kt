/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.knit.pathsaver.DocumentableType
import kotlinx.knit.pathsaver.LINK_INDEX_FILE
import kotlinx.knit.pathsaver.LinkIndexEntry
import org.jetbrains.dokka.links.Nullable
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.links.TypeReference
import java.io.File
import java.io.FileNotFoundException
import java.util.*

data class ApiIndexKey(
    val docsRoot: String,
    val moduleName: String,
    val pkg: String
)

class ApiLink(val link: String, val type: DocumentableType)

// always prefer classes to functions, then prefer shorter links
private val apiLinkComparator = compareBy(ApiLink::type, { it.link.length })

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
            m[name]?.minWithOrNull(apiLinkComparator)?.link
}

private fun ApiIndex.addName(pkg: String, name: String, path: String, link: String, type: DocumentableType, namePrefix: String) {
    val apiLink = ApiLink("$path/$link", type)
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
        indexDirective: String,
        moduleName: String
): ApiIndex? {
    val pkg = indexDirective.substringAfter("/")

    return findFileOrNull((rootDir / docsRoot), LINK_INDEX_FILE)?.let {
        parseLinkIndexFile(it, pkg, moduleName)
    }
}

private fun parseLinkIndexFile(file: File, pkg: String, moduleName: String): ApiIndex {
    val projectIndex = jacksonObjectMapper().readValue<List<LinkIndexEntry>>(file)
    fun TypeReference.name(): String = when (this) {
        is TypeConstructor -> fullyQualifiedName
        is Nullable -> wrapped.name()
        else -> ""
    }

    return ApiIndex().apply {
        projectIndex
                .filter { it.dri.extra?.equals(moduleName, ignoreCase = true) ?: true  }
                .filter { it.dri.packageName?.equals(pkg, ignoreCase = true) ?: true  }
                .filter { it.type != DocumentableType.Parameter } // we don't want links to functions' parameters
                .forEach { entry ->
                    val pkgName = entry.dri.packageName.orEmpty()
                    val isFunction = entry.type == DocumentableType.Function // type is null when the link is to an external entity, so we assume that it may be a function
                    val classNames = entry.dri.classNames
                    val callable = entry.dri.callable
                    val receiverName = callable?.receiver?.name()?.takeIf(String::isNotBlank)?.let { "$it." }.orEmpty()
                    val path = entry.location.substringBefore("/")
                    val location = entry.location.substringAfter("/")

                    if (callable != null) {
                        addName(pkgName, callable.name, path, location, entry.type, "")
                        if (isFunction)
                            addName(pkgName, callable.name + "()", path, location, entry.type, "")
                        if (classNames == null) {
                            addName(pkgName, callable.name, path, location, entry.type, receiverName)
                            if (isFunction)
                                addName(pkgName, callable.name + "()", path, location, entry.type, receiverName)
                        } else {
                            addName(pkgName, callable.name, path, location, entry.type, "$classNames.")
                            if (isFunction)
                                addName(pkgName, callable.name + "()", path, location, entry.type, "$classNames.")
                        }
                    } else {
                        if (classNames != null) {
                            addName(pkgName, classNames, path, location, entry.type, "")
                        }
                    }
                }
    }
}

private fun findFileOrNull(fileDir: File, vararg names: String): File? = runCatching { findFile(fileDir, *names) }.getOrNull()

private fun findFile(fileDir: File, vararg names: String): File =
        names.map { fileDir / it }.firstOrNull { it.exists() }
                ?: throw FileNotFoundException("Cannot find one of files ${names.joinToString(", ") { "'$it'" }} in $fileDir")

fun KnitContext.processApiIndex(
    inputFile: File,
    siteRoot: String,
    moduleName: String,
    docsRoot: String,
    pkg: String,
    remainingApiRefNames: MutableSet<String>,
    uppercaseApiRefNames: HashMap<String, String>
): List<String>? {
    val key = ApiIndexKey(docsRoot, moduleName, pkg)
    val index = apiIndexCache.getOrPut(key) {
        val result = loadApiIndex(docsRoot, pkg, moduleName) ?: return null // null on failure
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
        val oldNameCase = uppercaseApiRefNames.put(refName.uppercase(), refName)
        if (oldNameCase != null) {
            log.warn("WARNING: $inputFile: References [$refName] and [$oldNameCase] are different only in case, not distinguishable in markdown.")
        }
    }
    return indexList
}
