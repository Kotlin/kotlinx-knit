/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*
import java.util.*
import kotlin.properties.*
import kotlin.system.*

// --- props that can be defined in "knit.properties" files

const val TEST_NAME_PROP = "test.name"
const val TEST_DIR_PROP = "test.dir"
const val TEST_INCLUDE_PROP = "test.include"

const val KNIT_PACKAGE_PROP = "knit.package"
const val KNIT_PATTERN_PROP = "knit.pattern"
const val KNIT_DIR_PROP = "knit.dir"
const val KNIT_INCLUDE_PROP = "knit.include"

// --- markdown syntax

const val DIRECTIVE_START = "<!--- "
const val DIRECTIVE_END = "-->"

const val TOC_DIRECTIVE = "TOC"
const val TOC_REF_DIRECTIVE = "TOC_REF"
const val INCLUDE_DIRECTIVE = "INCLUDE"
const val CLEAR_DIRECTIVE = "CLEAR"
const val TEST_DIRECTIVE = "TEST"

const val KNIT_AUTONUMBER_PLACEHOLDER = '#'
const val KNIT_AUTONUMBER_REGEX = "([0-9a-z]+)"

const val TEST_NAME_DIRECTIVE = "TEST_NAME"

const val MODULE_DIRECTIVE = "MODULE"
const val INDEX_DIRECTIVE = "INDEX"

const val CODE_START = "```kotlin"
const val CODE_END = "```"

const val SAMPLE_START = "//sampleStart"
const val SAMPLE_END = "//sampleEnd"

const val TEST_START = "```text"
const val TEST_END = "```"

const val SECTION_START = "##"

const val STARTS_WITH_PREDICATE = "STARTS_WITH"
const val ARBITRARY_TIME_PREDICATE = "ARBITRARY_TIME"
const val FLEXIBLE_TIME_PREDICATE = "FLEXIBLE_TIME"
const val FLEXIBLE_THREAD_PREDICATE = "FLEXIBLE_THREAD"
const val LINES_START_UNORDERED_PREDICATE = "LINES_START_UNORDERED"
const val EXCEPTION_MODE = "EXCEPTION"
const val LINES_START_PREDICATE = "LINES_START"

val API_REF_REGEX = Regex("(^|[ \\](])\\[([A-Za-z0-9_().]+)]($|[^\\[(])")
val LINK_DEF_REGEX = Regex("^\\[([A-Za-z0-9_().]+)]: .*")

// ----------------------------------------------------

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: Knit <markdown-files>")
        exitProcess(1)
    }
    if (!runKnit(args.map { File(it) })) exitProcess(2)
}

fun runKnit(files: List<File>): Boolean = createDefaultContext(files).process()

fun KnitContext.process(): Boolean {
    while (!fileQueue.isEmpty()) {
        if (!knit(fileQueue.removeFirst())) return false
    }
    return true
}

class KnitConfig(
    val path: String,
    val regex: Regex,
    val autonumberDigits: Int
)

fun KnitProps.knitConfig(): KnitConfig? {
    val dir = this[KNIT_DIR_PROP] ?: return null
    var pattern = getValue(KNIT_PATTERN_PROP)
    val i = pattern.indexOf(KNIT_AUTONUMBER_PLACEHOLDER)
    var autonumberDigits = 0
    if (i >= 0) {
        val j = pattern.lastIndexOf(KNIT_AUTONUMBER_PLACEHOLDER)
        autonumberDigits = j - i + 1
        require(pattern.substring(i, j + 1) == KNIT_AUTONUMBER_PLACEHOLDER.toString().repeat(autonumberDigits)) {
            "$KNIT_PATTERN_PROP property can only use a contiguous range of '$KNIT_AUTONUMBER_PLACEHOLDER' for auto-numbering"
        }
        require('(' !in pattern && ')' !in pattern) {
            "$KNIT_PATTERN_PROP property cannot have match groups"
        }
        pattern = pattern.substring(0, i) + KNIT_AUTONUMBER_REGEX + pattern.substring(j + 1)
    }
    val path = "$dir($pattern)"
    return KnitConfig(path, Regex("\\(($path)\\)"), autonumberDigits)
}

@Suppress("unused") // This class is passed to freemarker template
class KnitIncludeEnv(
    val file: File,
    props: KnitProps,
    knitName: String
) {
    val knit = props.getMap("knit") + mapOf("name" to knitName)
}

fun KnitConfig.loadMainInclude(file: File, props: KnitProps, knitName: String): Include {
    val include = Include(Regex(path))
    include.lines += props.loadTemplateLines(KNIT_INCLUDE_PROP, KnitIncludeEnv(file, props, knitName))
    include.lines += ""
    return include
}

// Reference to knitted example's full package (pkg.name)
class KnitRef(val pkg: String, val name: String) {
    override fun toString(): String = "$pkg.$name"
}

fun KnitContext.knit(markdownFile: File): Boolean {
    log.info("*** Reading $markdownFile")
    val props = findProps(markdownFile, rootDir)
    val knit = props.knitConfig()
    val knitAutonumberIndex = HashMap<String, Int>()
    val tocLines = arrayListOf<String>()
    val includes = arrayListOf<Include>()
    val codeLines = arrayListOf<String>()
    val testLines = arrayListOf<String>()
    var testName: String? = props[TEST_NAME_PROP]
    val testOutLines = arrayListOf<String>()
    var lastKnit: KnitRef? = null
    val files = mutableSetOf<File>()
    val allApiRefs = arrayListOf<ApiRef>()
    val remainingApiRefNames = mutableSetOf<String>()
    var moduleName: String by Delegates.notNull()
    var docsRoot: String by Delegates.notNull()
    var retryKnitLater = false
    val tocRefs = ArrayList<TocRef>().also { tocRefMap[markdownFile] = it }
    // read markdown file
    val markdown = withMarkdownTextReader(markdownFile) {
        mainLoop@ while (true) {
            val inLine = readLine() ?: break
            val directive = directive(inLine)
            if (directive != null && markdownPart == MarkdownPart.TOC) {
                markdownPart = MarkdownPart.POST_TOC
                postTocText += inLine
            }
            when (directive?.name) {
                TOC_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isEmpty()) { "$TOC_DIRECTIVE directive must not have parameters" }
                    require(markdownPart == MarkdownPart.PRE_TOC) { "Only one TOC directive is supported" }
                    markdownPart = MarkdownPart.TOC
                }
                TOC_REF_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(!directive.param.isEmpty()) { "$TOC_REF_DIRECTIVE directive must include reference file path" }
                    val refPath = directive.param
                    val refFile = File(markdownFile.parent, refPath.replace('/', File.separatorChar))
                    require(fileSet.contains(refFile)) { "Referenced file $refFile is missing from the processed file set" }
                    val toc = tocRefMap[refFile]
                    if (toc == null) {
                        retryKnitLater = true // put this file at the end of the queue and retry later
                    } else {
                        val lines = toc.map { (levelPrefix, name, ref) ->
                            "$levelPrefix <a name='$ref'></a>[$name]($refPath#$ref)"
                        }
                        if (!replaceUntilNextDirective(lines)) error("Unexpected end of file after $TOC_REF_DIRECTIVE")
                    }
                }
                INCLUDE_DIRECTIVE -> {
                    if (directive.param.isEmpty()) {
                        require(!directive.singleLine) { "$INCLUDE_DIRECTIVE directive without parameters must not be single line" }
                        readUntilTo(DIRECTIVE_END, codeLines)
                    } else {
                        val include = Include(Regex(directive.param))
                        if (directive.singleLine) {
                            include.lines += codeLines
                            codeLines.clear()
                        } else {
                            readUntilTo(DIRECTIVE_END, include.lines)
                        }
                        includes += include
                    }
                    continue@mainLoop
                }
                CLEAR_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isEmpty()) { "$CLEAR_DIRECTIVE directive must not have parameters" }
                    codeLines.clear()
                    continue@mainLoop
                }
                TEST_NAME_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isNotEmpty()) { "$TEST_NAME_DIRECTIVE directive must include name parameter" }
                    flushTestOut(markdownFile, props, testName, testOutLines)
                    testName = directive.param
                }
                TEST_DIRECTIVE -> {
                    require(lastKnit != null) { "$TEST_DIRECTIVE must be preceded by knitted file" }
                    require(testName != null) { "Neither $TEST_NAME_DIRECTIVE directive nor '$TEST_NAME_PROP'property was specified" }
                    val predicate = directive.param
                    if (testLines.isEmpty()) {
                        if (directive.singleLine) {
                            require(predicate.isNotEmpty()) { "$TEST_DIRECTIVE must be preceded by $TEST_START block or contain test predicate"}
                        } else
                            testLines += readUntil(DIRECTIVE_END)
                    } else {
                        requireSingleLine(directive)
                    }
                    makeTest(testOutLines, lastKnit!!, testLines, predicate)
                    testLines.clear()
                }
                MODULE_DIRECTIVE -> {
                    requireSingleLine(directive)
                    moduleName = directive.param
                    docsRoot = findModuleRoot(moduleName) + "/" + moduleDocs + "/" + moduleName
                }
                INDEX_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(siteRoot != null) { "Missing 'siteRoot' in knit configuration, cannot do $INDEX_DIRECTIVE" }
                    val indexLines = processApiIndex(
                        "$siteRoot/$moduleName", docsRoot, directive.param, remainingApiRefNames
                    )
                        ?: throw IllegalArgumentException("Failed to load index for ${directive.param}")
                    if (!replaceUntilNextDirective(indexLines)) error("Unexpected end of file after $INDEX_DIRECTIVE")
                }
            }
            if (inLine.startsWith(CODE_START)) {
                require(testName == null || testLines.isEmpty()) { "Previous test was not emitted with $TEST_DIRECTIVE" }
                if (codeLines.lastOrNull()?.isNotBlank() == true) codeLines += ""
                readUntilTo(CODE_END, codeLines) { line ->
                    !line.startsWith(SAMPLE_START) && !line.startsWith(SAMPLE_END)
                }
                continue@mainLoop
            }
            if (inLine.startsWith(TEST_START)) {
                require(testName == null || testLines.isEmpty()) { "Previous test was not emitted with $TEST_DIRECTIVE" }
                readUntilTo(TEST_END, testLines)
                continue@mainLoop
            }
            if (inLine.startsWith(SECTION_START) && markdownPart == MarkdownPart.POST_TOC) {
                val i = inLine.indexOf(' ')
                require(i >= 2) { "Invalid section start" }
                val name = inLine.substring(i + 1).trim()
                val levelPrefix = "  ".repeat(i - 2) + "*"
                val sectionRef = makeSectionRef(name)
                tocLines += "$levelPrefix [$name](#$sectionRef)"
                tocRefs += TocRef(levelPrefix, name, sectionRef)
                continue@mainLoop
            }
            val linkDefMatch = LINK_DEF_REGEX.matchEntire(inLine)
            if (linkDefMatch != null) {
                val name = linkDefMatch.groups[1]!!.value
                remainingApiRefNames -= name
            } else {
                for (match in API_REF_REGEX.findAll(inLine)) {
                    val apiRef = ApiRef(lineNumber, match.groups[2]!!.value)
                    allApiRefs += apiRef
                    remainingApiRefNames += apiRef.name
                }
            }
            knit?.regex?.find(inLine)?.let knitRegexMatch@{ knitMatch ->
                val path = knitMatch.groups[1]!!.value // full matched knit path dir dir & file name
                val fileGroup = knitMatch.groups[2]!!
                val fileName = fileGroup.value // knitted file name like "example-basic-01.kt"
                if (knit.autonumberDigits != 0) {
                    val numGroup = knitMatch.groups[3]!! // file number part like "01"
                    val key = inLine.substring(fileGroup.range.first, numGroup.range.first) +
                            inLine.substring(numGroup.range.last + 1, fileGroup.range.last + 1)
                    val index = knitAutonumberIndex.getOrElse(key) { 1 }
                    val num = index.toString().padStart(knit.autonumberDigits, '0')
                    if (numGroup.value != num) { // update and retry with this line if a different number
                        val r = numGroup.range
                        val newLine = inLine.substring(0, r.first) + num + inLine.substring(r.last + 1)
                        updateLineAndRetry(newLine)
                        return@knitRegexMatch
                    }
                    knitAutonumberIndex[key] = index + 1
                }
                val file = File(markdownFile.parentFile, path)
                require(files.add(file)) { "Duplicate file: $file"}
                log.info("Knitting $file ...")
                val outLines = arrayListOf<String>()
                val fileIncludes = arrayListOf<Include>()
                // load & process template of the main include
                val knitName = fileName.toKnitName()
                fileIncludes += knit.loadMainInclude(markdownFile, props, knitName)
                fileIncludes += includes.filter { it.regex.matches(path) }
                for (include in fileIncludes) outLines += include.lines
                if (outLines.last().isNotBlank()) outLines += ""
                for (code in codeLines) {
                    outLines += code.replace("System.currentTimeMillis()", "currentTimeMillis()")
                }
                codeLines.clear()
                writeLinesIfNeeded(file, outLines)
                lastKnit = KnitRef(props.getValue(KNIT_PACKAGE_PROP), knitName)
            }
        }
    } ?: return false // false when failed
    // bailout if retry was requested
    if (retryKnitLater) {
        fileQueue.add(markdownFile)
        return true
    }
    // update markdown file with toc
    val newLines = buildList<String> {
        addAll(markdown.preTocText)
        if (!tocLines.isEmpty()) {
            add("")
            addAll(tocLines)
            add("")
        }
        addAll(markdown.postTocText)
    }
    if (newLines != markdown.inText) writeLines(markdownFile, newLines)
    // check apiRefs
    for (apiRef in allApiRefs) {
        if (apiRef.name in remainingApiRefNames) {
            log.warn("WARNING: $markdownFile: ${apiRef.line}: Broken reference to [${apiRef.name}]")
        }
    }
    // write test output
    flushTestOut(markdownFile, props, testName, testOutLines)
    return true
}

// Converts file name like "example-basic-01.kt" to unique knit.name for package like "exampleBasic01"
private fun String.toKnitName(): String = substringBefore('.').capitalizeAfter('-')

private fun String.capitalizeAfter(char: Char): String = buildString {
    var cap = false
    for (c in this@capitalizeAfter) {
        cap = if (c == char) true else {
            append(if (cap) c.toUpperCase() else c)
            false
        }
    }
}

data class TocRef(val levelPrefix: String, val name: String, val ref: String)

fun makeTest(testOutLines: MutableList<String>, knit: KnitRef, test: List<String>, predicate: String) {
    val funName = knit.name.capitalize()
    testOutLines += ""
    testOutLines += "    @Test"
    testOutLines += "    fun test$funName() {"
    val prefix = "        test(\"$funName\") { $knit.main() }"
    when (predicate) {
        "" -> makeTestLines(testOutLines, prefix, "verifyLines", test)
        STARTS_WITH_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesStartWith", test)
        ARBITRARY_TIME_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesArbitraryTime", test)
        FLEXIBLE_TIME_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesFlexibleTime", test)
        FLEXIBLE_THREAD_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesFlexibleThread", test)
        LINES_START_UNORDERED_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesStartUnordered", test)
        EXCEPTION_MODE -> makeTestLines(testOutLines, prefix, "verifyExceptions", test)
        LINES_START_PREDICATE -> makeTestLines(testOutLines, prefix, "verifyLinesStart", test)
        else -> {
            testOutLines += "$prefix.also { lines ->"
            testOutLines += "            check($predicate)"
            testOutLines += "        }"
        }
    }
    testOutLines += "    }"
}

private fun makeTestLines(testOutLines: MutableList<String>, prefix: String, method: String, test: List<String>) {
    testOutLines += "$prefix.$method("
    for ((index, testLine) in test.withIndex()) {
        val commaOpt = if (index < test.size - 1) "," else ""
        val escapedLine = testLine.replace("\"", "\\\"")
        testOutLines += "            \"$escapedLine\"$commaOpt"
    }
    testOutLines += "        )"
}

@Suppress("unused") // This class is passed to freemarker template
class TestTemplateEnv(
    val file: File,
    props: KnitProps,
    testName: String
) {
    val test = props.getMap("test") + mapOf("name" to testName)
}

private fun KnitContext.flushTestOut(file: File, props: KnitProps, testName: String?, testOutLines: MutableList<String>) {
    if (testOutLines.isEmpty()) return
    if (testName == null) return
    val lines = arrayListOf<String>()
    lines += props.loadTemplateLines(TEST_INCLUDE_PROP, TestTemplateEnv(file, props, testName))
    lines += testOutLines
    lines += "}"
    val testFile = File(props.getFile(TEST_DIR_PROP), "$testName.kt")
    log.info("Checking $testFile")
    writeLinesIfNeeded(testFile, lines)
    testOutLines.clear()
}

private fun MarkdownTextReader.readUntil(marker: String): List<String> =
    arrayListOf<String>().also { readUntilTo(marker, it) }

private fun MarkdownTextReader.readUntilTo(marker: String, list: MutableList<String>, linePredicate: (String) -> Boolean = { true }) {
    while (true) {
        val line = readLine() ?: break
        if (line.startsWith(marker)) break
        if (linePredicate(line)) list += line
    }
}

private inline fun <T> buildList(block: ArrayList<T>.() -> Unit): List<T> {
    val result = arrayListOf<T>()
    result.block()
    return result
}

private fun requireSingleLine(directive: Directive) {
    require(directive.singleLine) { "${directive.name} directive must end on the same line with '$DIRECTIVE_END'" }
}

fun makeSectionRef(name: String): String = name
    .replace(' ', '-')
    .replace(".", "")
    .replace(",", "")
    .replace("(", "")
    .replace(")", "")
    .replace("`", "")
    .toLowerCase()

class Include(val regex: Regex, val lines: MutableList<String> = arrayListOf())

class Directive(
    val name: String,
    val param: String,
    val singleLine: Boolean
)

fun directive(line: String): Directive? {
    if (!line.startsWith(DIRECTIVE_START)) return null
    var s = line.substring(DIRECTIVE_START.length).trim()
    val singleLine = s.endsWith(DIRECTIVE_END)
    if (singleLine) s = s.substring(0, s.length - DIRECTIVE_END.length)
    val i = s.indexOf(' ')
    val name = if (i < 0) s else s.substring(0, i)
    val param = if (i < 0) "" else s.substring(i).trim()
    return Directive(name, param, singleLine)
}

class ApiRef(val line: Int, val name: String)

enum class MarkdownPart { PRE_TOC, TOC, POST_TOC }

class MarkdownTextReader(r: Reader) : LineNumberReader(r) {
    val inText = arrayListOf<String>()
    val preTocText = arrayListOf<String>()
    val postTocText = arrayListOf<String>()
    var markdownPart: MarkdownPart = MarkdownPart.PRE_TOC
    var skip = false
    var putBackLine: String? = null

    val outText: MutableList<String> get() = when (markdownPart) {
        MarkdownPart.PRE_TOC -> preTocText
        MarkdownPart.POST_TOC -> postTocText
        else -> throw IllegalStateException("Wrong state: $markdownPart")
    }

    override fun readLine(): String? {
        putBackLine?.let {
            putBackLine = null
            return it
        }
        val line = super.readLine() ?: return null
        inText += line
        if (!skip && markdownPart != MarkdownPart.TOC)
            outText += line
        return line
    }

    fun updateLineAndRetry(line: String) {
        outText.removeAt(outText.lastIndex)
        outText += line
        putBackLine = line
    }

    fun replaceUntilNextDirective(lines: List<String>): Boolean {
        skip = true
        while (true) {
            val skipLine = readLine() ?: return false
            if (directive(skipLine) != null) {
                putBackLine = skipLine
                break
            }
        }
        skip = false
        outText += lines
        outText += putBackLine!!
        return true
    }
}

fun KnitContext.withMarkdownTextReader(file: File, block: MarkdownTextReader.() -> Unit): MarkdownTextReader? =
    withLineNumberReader(file, ::MarkdownTextReader, block)

fun KnitContext.writeLinesIfNeeded(file: File, outLines: List<String>) {
    val oldLines = try {
        file.readLines()
    } catch (e: IOException) {
        null
    }
    if (outLines != oldLines) {
        if (check) {
            val text = formatOutdated(oldLines, outLines)
            log.outdated("WARNING: $file: $text")
        } else {
            writeLines(file, outLines)
        }
    }
}

private fun formatOutdated(oldLines: List<String>?, outLines: List<String>) =
    if (oldLines == null)
        "is missing"
    else {
        val msg = diffErrorMessage(formatDiff(oldLines, outLines))
        "is not up-to-date, $msg"
    }

fun KnitContext.writeLines(file: File, lines: List<String>) {
    log.info(" Writing $file ...")
    file.parentFile?.mkdirs()
    file.printWriter().use { out ->
        lines.forEach { out.println(it) }
    }
}

fun KnitContext.findModuleRoot(name: String): String =
    moduleRoots
        .map { "$it/$name" }
        .firstOrNull { dir -> moduleMarkers.any { (rootDir / "$dir/$it").exists() } }
        ?: throw IllegalArgumentException("Module $name is not found in any of the module root dirs")
