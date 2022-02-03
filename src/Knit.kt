/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import kotlinx.knit.test.*
import java.io.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.properties.*
import kotlin.system.*

// --- props that can be defined in "knit.properties" files

const val TEST_NAME_PROP = "test.name"
const val TEST_DIR_PROP = "test.dir"
const val TEST_TEMPLATE_PROP = "test.template"
const val TEST_LANGUAGE_PROP = "test.language"

const val KNIT_PATTERN_PROP = "knit.pattern"
const val KNIT_DIR_PROP = "knit.dir"
const val KNIT_INCLUDE_PROP = "knit.include"
const val KNIT_LANGUAGE_PROP = "knit.language"

// --- markdown syntax

const val DIRECTIVE_START = "<!--- "
const val DIRECTIVE_NEXT = "----- "
const val DIRECTIVE_END = "-->"
val DIRECTIVE_REGEX = Regex("$DIRECTIVE_START\\s*([_A-Z]+)(?:\\s+(.+?(?=$DIRECTIVE_END|)))?(?:\\s*($DIRECTIVE_END))?\\s*")

const val TOC_DIRECTIVE = "TOC"
const val END_DIRECTIVE = "END"
const val TOC_REF_DIRECTIVE = "TOC_REF"
const val INCLUDE_DIRECTIVE = "INCLUDE"
const val PREFIX_DIRECTIVE = "PREFIX"
const val SUFFIX_DIRECTIVE = "SUFFIX"
const val CLEAR_DIRECTIVE = "CLEAR"
const val KNIT_DIRECTIVE = "KNIT"
const val TEST_DIRECTIVE = "TEST"

const val KNIT_AUTONUMBER_PLACEHOLDER = '#'
const val KNIT_AUTONUMBER_REGEX = "([0-9a-z]+)"

const val TEST_NAME_DIRECTIVE = "TEST_NAME"

const val MODULE_DIRECTIVE = "MODULE"
const val INDEX_DIRECTIVE = "INDEX"

const val CODE_START = "```" // + knit.language
const val CODE_END = "```"

const val SAMPLE_START = "//sampleStart"
const val SAMPLE_END = "//sampleEnd"

const val TEST_START = "```" // + test.language
const val TEST_END = "```"

const val SECTION_START = "##"

val API_REF_REGEX = Regex("(^|[ \\](])\\[([A-Za-z0-9_().]+)]($|[^\\[(])")
val LINK_DEF_REGEX = Regex("^\\[([A-Za-z0-9_().]+)]: .*")

// ----------------------------------------------------

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: Knit <input-files>")
        exitProcess(1)
    }
    val context = createDefaultContext(args.map { File(it) })
    if (!context.process()) exitProcess(2)
}

fun KnitContext.process(): Boolean {
    while (!fileQueue.isEmpty()) {
        if (!knit(fileQueue.removeFirst())) return false
    }
    return true
}

class KnitConfig(
    val knitDir: File, // Resolved path to the knit.dir, e.g. "src/path/"
    val referenceRegex: Regex, // Regex for in-text references to knitted file, e.g "(../src/path/example-foo-01.kt)"
    val nameRegex: Regex, // Regex for KNIT command references, without a path, e.g. "example-foo-01.kt"
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
    return KnitConfig(
        knitDir = getFile(KNIT_DIR_PROP),
        referenceRegex = Regex("\\(($dir($pattern))\\)"),
        nameRegex = Regex(pattern),
        autonumberDigits = autonumberDigits
    )
}

private operator fun MatchGroup.plus(offset: Int) =
    MatchGroup(value, IntRange(range.first + offset, range.last + offset))

@Suppress("unused") // This class is passed to freemarker template
class KnitIncludeEnv(
    val file: File,
    props: KnitProps,
    knitName: String
) {
    val knit = props.getMap("knit") + mapOf("name" to knitName)
}

fun loadMainInclude(file: File, props: KnitProps, knitName: String): List<String> =
    props.loadTemplateLines(KNIT_INCLUDE_PROP, KnitIncludeEnv(file, props, knitName)) +
    "" // empty line after the main include

// Reference to knitted example's full package (pkg.name)
class KnitRef(val props: KnitProps, val name: String)

fun KnitContext.knit(inputFile: File): Boolean {
    log.info("*** Reading $inputFile")
    val props = findProps(inputFile, rootDir)
    val knit = props.knitConfig()
    val knitAutonumberIndex = HashMap<String, Int>()
    val tocLines = arrayListOf<String>()
    val includes = arrayListOf<Include>()
    val codeStartLang = CODE_START + props.getValue(KNIT_LANGUAGE_PROP)
    val prefixLines = arrayListOf<String>()
    val suffixLines = arrayListOf<String>()
    val codeLines = arrayListOf<String>()
    val testStartLang = TEST_START + props.getValue(TEST_LANGUAGE_PROP)
    val testLines = arrayListOf<String>()
    var testName: String? = props[TEST_NAME_PROP]
    val testCases = arrayListOf<TestCase>()
    var lastKnit: KnitRef? = null
    val files = mutableSetOf<File>()
    val allApiRefs = arrayListOf<ApiRef>()
    val remainingApiRefNames = mutableSetOf<String>()
    val uppercaseApiRefNames = HashMap<String, String>()
    var moduleName: String by Delegates.notNull()
    var docsRoot: String by Delegates.notNull()
    var retryKnitLater = false
    val tocRefs = ArrayList<TocRef>().also { tocRefMap[inputFile] = it }
    // read input file
    val inputFileType = inputFile.type()
    if (inputFileType == InputFileType.UNKNOWN) {
        log.warn("WARNING: $inputFile: Unknown input file type. Treating it as markdown.")
    }
    val markdown = withInputTextReader(inputFile, inputFileType) {
        mainLoop@ while (true) {
            val inLine = readLine() ?: break
            val lineStartIndex = inputFileType.lineStartIndex(inLine)
            val linePrefix = inLine.substring(0, lineStartIndex)
            val directive = directive(inLine, lineStartIndex)
            if (directive != null && inputTextPart == InputTextPart.TOC) {
                inputTextPart = InputTextPart.POST_TOC
                postTocText += inLine
            }
            // This function is called on a KNIT directive and on a knit in-text reference
            fun KnitConfig.doKnit(file: File, fileMatch: MatchGroup, numberMatch: MatchGroup) {
                if (autonumberDigits != 0) {
                    val key = inLine.substring(fileMatch.range.first, numberMatch.range.first) +
                            inLine.substring(numberMatch.range.last + 1, fileMatch.range.last + 1)
                    val index = knitAutonumberIndex.getOrElse(key) { 1 }
                    val num = index.toString().padStart(autonumberDigits, '0')
                    if (numberMatch.value != num) { // update and retry with this line if a different number
                        val r = numberMatch.range
                        val newLine = inLine.substring(0, r.first) + num + inLine.substring(r.last + 1)
                        updateLineAndRetry(newLine)
                        return
                    }
                    knitAutonumberIndex[key] = index + 1
                }
                require(files.add(file)) { "Duplicate file: $file"}
                log.info("Knitting $file ...")
                // -- PREFIX --
                val outLines = prefixLines.toMutableList()
                includes
                    .filter { it.type == IncludeType.PREFIX && it.nameRegex.matches(file.name) }
                    .forEach { outLines += it.lines }
                // -- Load & process template of the main include --
                val knitName = file.name.toKnitName()
                outLines += loadMainInclude(inputFile, props, knitName)
                // -- INCLUDE --
                includes
                    .filter { it.type == IncludeType.INCLUDE && it.nameRegex.matches(file.name) }
                    .forEach { outLines += it.lines }
                // -- The main code
                if (outLines.last().isNotBlank()) outLines += ""
                for (code in codeLines) {
                    outLines += code.replace("System.currentTimeMillis()", "currentTimeMillis()")
                }
                // -- SUFFIX --
                includes
                    .filter { it.type == IncludeType.SUFFIX && it.nameRegex.matches(file.name) }
                    .forEach { outLines += it.lines }
                outLines += suffixLines
                // -- Finalize and write --
                prefixLines.clear()
                suffixLines.clear()
                codeLines.clear()
                writeLinesIfNeeded(file, outLines)
                lastKnit = KnitRef(props, knitName)
            }
            // Processes INCLUDE and PREFIX directives with pattern
            fun saveInclude(directive: Directive, type: IncludeType) {
                require(directive.param.isNotEmpty())
                // Note: Trim legacy .*/ prefix that is not needed in INCLUDE patterns anymore, only name matches
                val namePattern = directive.param.removePrefix(".*/")
                val include = Include(type, Regex(namePattern))
                if (directive.singleLine) {
                    include.lines += codeLines
                    codeLines.clear()
                } else {
                    readUntilToDirectiveEnd(linePrefix, include.lines)
                }
                includes += include
            }
            // Match directives
            when (directive?.name) {
                null, END_DIRECTIVE -> { /* do nothing, END works like NOP, too */ }
                TOC_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isEmpty()) { "$TOC_DIRECTIVE directive must not have parameters" }
                    require(inputTextPart == InputTextPart.PRE_TOC) { "Only one TOC directive is supported" }
                    inputTextPart = InputTextPart.TOC
                }
                TOC_REF_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isNotEmpty()) { "$TOC_REF_DIRECTIVE directive must include reference file path" }
                    val refPath = directive.param
                    val refFile = File(inputFile.parent, refPath.replace('/', File.separatorChar))
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
                        readUntilToDirectiveEnd(linePrefix, codeLines)
                    } else {
                        saveInclude(directive, IncludeType.INCLUDE)
                    }
                    continue@mainLoop
                }
                PREFIX_DIRECTIVE -> {
                    if (directive.param.isEmpty()) {
                        if (directive.singleLine) {
                            prefixLines += codeLines
                            codeLines.clear()
                        } else {
                            readUntilToDirectiveEnd(linePrefix, prefixLines)
                        }
                    } else {
                        saveInclude(directive, IncludeType.PREFIX)
                    }
                    continue@mainLoop
                }
                SUFFIX_DIRECTIVE -> {
                    if (directive.param.isEmpty()) {
                        if (directive.singleLine) {
                            suffixLines += codeLines
                            codeLines.clear()
                        } else {
                            readUntilToDirectiveEnd(linePrefix, suffixLines)
                        }
                    } else {
                        saveInclude(directive, IncludeType.SUFFIX)
                    }
                    continue@mainLoop
                }
                CLEAR_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isEmpty()) { "$CLEAR_DIRECTIVE directive must not have parameters" }
                    codeLines.clear()
                    continue@mainLoop
                }
                KNIT_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(knit != null) { "'$KNIT_DIR_PROP' property must be configured to use $KNIT_DIRECTIVE directive" }
                    val match = knit.nameRegex.matchEntire(directive.param)
                    require(match != null) { "$KNIT_DIRECTIVE directive parameter must match '$KNIT_PATTERN_PROP' property pattern" }
                    knit.doKnit(
                        file = File(knit.knitDir, directive.param),
                        fileMatch = match.groups[0]!! + directive.paramOffset,
                        numberMatch = match.groups[1]!! + directive.paramOffset
                    )
                }
                TEST_NAME_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(directive.param.isNotEmpty()) { "$TEST_NAME_DIRECTIVE directive must include name parameter" }
                    flushTestOut(inputFile, props, testName, testCases)
                    testName = directive.param
                }
                TEST_DIRECTIVE -> {
                    require(lastKnit != null) { "$TEST_DIRECTIVE must be preceded by knitted file" }
                    require(testName != null) { "Neither $TEST_NAME_DIRECTIVE directive nor '$TEST_NAME_PROP' property was specified" }
                    val param = directive.param
                    if (testLines.isEmpty()) {
                        if (directive.singleLine) {
                            require(param.isNotEmpty()) { "$TEST_DIRECTIVE must be preceded by $testStartLang block or contain test parameter"}
                        } else
                            readUntilToDirectiveEnd(linePrefix, testLines)
                    } else {
                        requireSingleLine(directive)
                    }
                    testCases += makeTest(lastKnit!!, testLines.toList(), param)
                    testLines.clear()
                }
                MODULE_DIRECTIVE -> {
                    requireSingleLine(directive)
                    val isRoot = directive.param.startsWith('/')
                    moduleName = if (isRoot) directive.param.substring(1) else directive.param
                    docsRoot = findMultiModuleRoot() ?: findModuleRoot(isRoot, moduleName) + "/" + moduleDocs + "/" + moduleName
                }
                INDEX_DIRECTIVE -> {
                    requireSingleLine(directive)
                    require(siteRoot != null) { "Missing 'siteRoot' in knit configuration, cannot do $INDEX_DIRECTIVE" }
                    val indexLines = processApiIndex(
                        inputFile,
                        siteRoot,
                        moduleName,
                        docsRoot,
                        directive.param,
                        remainingApiRefNames,
                        uppercaseApiRefNames
                    )
                        ?: throw IllegalArgumentException("Failed to load index for ${directive.param}")
                    /*
                     * WebHelp format requires a newline between INDEX directive
                     * and actual references in order to properly parse them.
                     * Also: https://youtrack.jetbrains.com/issue/WH-2121
                     */
                    val result = if (indexLines.isEmpty()) {
                        indexLines
                    } else {
                        val tmp = ArrayList<String>(2 + indexLines.size)
                        tmp.add("")
                        tmp.addAll(indexLines)
                        tmp.add("")
                        tmp
                    }
                    if (!replaceUntilNextDirective(result)) error("Unexpected end of file after $INDEX_DIRECTIVE")
                }
                else -> {
                    error("Unrecognized knit directive '${directive.name}' on a line starting with '$DIRECTIVE_START'")
                }
            }
            if (inLine.startsWith(codeStartLang, lineStartIndex)) {
                require(testName == null || testLines.isEmpty()) { "Previous test was not emitted with $TEST_DIRECTIVE" }
                if (codeLines.lastOrNull()?.isNotBlank() == true) codeLines += ""
                readUntilTo(
                    linePrefix, codeLines,
                    endLine = { line -> line.startsWith(CODE_END, lineStartIndex) },
                    acceptLine = { line ->
                        !line.startsWith(SAMPLE_START, lineStartIndex) && !line.startsWith(SAMPLE_END, lineStartIndex)
                    }
                )
                continue@mainLoop
            }
            if (inLine.startsWith(testStartLang, lineStartIndex)) {
                require(testName == null || testLines.isEmpty()) { "Previous test was not emitted with $TEST_DIRECTIVE" }
                readUntilTo(
                    linePrefix, testLines,
                    endLine = { line -> line.startsWith(TEST_END, lineStartIndex) }
                )
                continue@mainLoop
            }
            if (inLine.startsWith(SECTION_START, lineStartIndex) && inputTextPart == InputTextPart.POST_TOC) {
                val i = inLine.indexOf(' ', lineStartIndex)
                require(i >= lineStartIndex + 2) { "Invalid section start" }
                val name = inLine.substring(i + 1).trim()
                val levelPrefix = "  ".repeat(i - 2) + "*"
                val sectionRef = makeSectionRef(name)
                tocLines += "$levelPrefix [$name](#$sectionRef)"
                tocRefs += TocRef(levelPrefix, name, sectionRef)
                continue@mainLoop
            }
            if (!inputFileType.ignoreTextRefs) {
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
                knit?.referenceRegex?.find(inLine)?.let {
                    knit.doKnit(
                        file = File(inputFile.parentFile, it.groups[1]!!.value),
                        fileMatch = it.groups[2]!!,
                        numberMatch = it.groups[3]!!
                    )
                }
            }
        }
    } ?: return false // false when failed
    // bailout if retry was requested
    if (retryKnitLater) {
        fileQueue.add(inputFile)
        return true
    }
    // update markdown file with toc
    val newLines = buildList<String> {
        addAll(markdown.preTocText)
        if (tocLines.isNotEmpty()) {
            add("")
            addAll(tocLines)
            add("")
        }
        addAll(markdown.postTocText)
    }
    if (newLines != markdown.inText) writeLines(inputFile, newLines)
    // check apiRefs
    for (apiRef in allApiRefs) {
        if (apiRef.name in remainingApiRefNames) {
            log.warn("WARNING: $inputFile: ${apiRef.line}: Broken reference to [${apiRef.name}]")
        }
    }
    // write test output
    flushTestOut(inputFile, props, testName, testCases)
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

fun makeTest(knit: KnitRef, testLines: List<String>, param: String): TestCase =
    TestCase(knit.props, knit.name, knit.name.capitalize(), param, testLines)

@Suppress("unused") // This class is passed to freemarker template
class TestTemplateEnv(
    val file: File,
    props: KnitProps,
    testName: String,
    val cases: List<TestCase>

) {
    val test = props.getMap("test", "name" to testName)
}

@Suppress("unused") // This class is passed to freemarker template
class TestCase(
    props: KnitProps,
    knitName: String,
    val name: String,
    val param: String,
    val lines: List<String>
) {
    val knit = props.getMap("knit", "name" to knitName)
}

private fun KnitContext.flushTestOut(file: File, props: KnitProps, testName: String?, testCases: MutableList<TestCase>) {
    if (testCases.isEmpty()) return
    if (testName == null) return
    val lines = props.loadTemplateLines(TEST_TEMPLATE_PROP, TestTemplateEnv(file, props, testName, testCases))
    val testFile = File(props.getFile(TEST_DIR_PROP), "$testName.kt")
    log.info("Checking $testFile")
    writeLinesIfNeeded(testFile, lines)
    testCases.clear()
}

private fun InputTextReader.readUntilTo(
    linePrefix: String,
    list: MutableList<String>,
    endLine: (String) -> Boolean,
    acceptLine: (String) -> Boolean = { true }
) {
    while (true) {
        val line = readLine() ?: break
        require(line.startsWith(linePrefix) || line == linePrefix.trimEnd()) {
            "Line must start with the same prefix '$linePrefix' as the first line"
        }
        if (endLine(line)) break
        if (acceptLine(line)) list += line.substring(minOf(linePrefix.length, line.length))
    }
}

private fun InputTextReader.readUntilToDirectiveEnd(linePrefix: String, list: MutableList<String>) =
    readUntilTo(
        linePrefix, list,
        endLine = { line ->
            when {
                line.startsWith(DIRECTIVE_END, linePrefix.length) -> true
                line.startsWith(DIRECTIVE_NEXT, linePrefix.length) -> {
                    putBackLine = linePrefix + DIRECTIVE_START + line.substring(DIRECTIVE_NEXT.length)
                    true
                }
                else -> false
            }
        }
    )

private inline fun <T> buildList(block: ArrayList<T>.() -> Unit): List<T> {
    val result = arrayListOf<T>()
    result.block()
    return result
}

private fun requireSingleLine(directive: Directive) {
    require(directive.singleLine) { "${directive.name} directive must end on the same line with '$DIRECTIVE_END'" }
}

private const val skippedTocSymbols = "\\,`*{}[]()/#+.!"

fun makeSectionRef(name: String): String = name
    .replace(' ', '-')
    .replace(("[" + Regex.escape(skippedTocSymbols) + "]").toRegex(), "")
    .toLowerCase()

enum class IncludeType { INCLUDE, PREFIX, SUFFIX }

class Include(
    val type: IncludeType,
    val nameRegex: Regex,
    val lines: MutableList<String> = arrayListOf()
)

class Directive(
    val name: String,
    val param: String,
    val paramOffset: Int,
    val singleLine: Boolean
)

private val skipWhitespace: (Char) -> Boolean = { it.isWhitespace() }
private val kotlinCommentPrefixes = listOf("// ", "* ")

enum class InputFileType(
    val extension: String,
    val skipLeadingChars: (Char) -> Boolean = { false },
    val directivePrefix: List<String> = emptyList(),
    val ignoreTextRefs: Boolean = false
) {
    MARKDOWN(".md"),
    KOTLIN(".kt", skipWhitespace, kotlinCommentPrefixes, ignoreTextRefs = true),
    KOTLIN_SCRIPT(".kts", skipWhitespace, kotlinCommentPrefixes, ignoreTextRefs = true),
    UNKNOWN("") // works just like MARKDOWN
}

fun File.type(): InputFileType = InputFileType.values().first { name.endsWith(it.extension) }

fun InputFileType.lineStartIndex(line: String): Int {
    var startIndex = 0
    while (startIndex < line.length && this.skipLeadingChars(line[startIndex])) startIndex++
    for (prefix in directivePrefix) {
        if (line.startsWith(prefix, startIndex)) {
            return startIndex + prefix.length
        }
    }
    return 0
}

fun directive(line: String, startIndex: Int): Directive? {
    if (!line.startsWith(DIRECTIVE_START, startIndex)) return null // fast check
    val match = DIRECTIVE_REGEX.matchEntire(line.substring(startIndex)) ?: return null
    val groups = match.groups.filterNotNull().toMutableList()
    val singleLine = groups.last().value == DIRECTIVE_END
    if (singleLine) groups.removeAt(groups.lastIndex)
    val name = groups[1].value
    val paramGroup = groups.getOrNull(2)
    val param = paramGroup?.value?.trimEnd() ?: ""
    val paramOffset = paramGroup?.range?.first ?: 0
    return Directive(name, param, startIndex + paramOffset, singleLine)
}

class ApiRef(val line: Int, val name: String)

enum class InputTextPart { PRE_TOC, TOC, POST_TOC }

class InputTextReader(val inputFileType: InputFileType, r: Reader) : LineNumberReader(r) {
    val inText = arrayListOf<String>()
    val preTocText = arrayListOf<String>()
    val postTocText = arrayListOf<String>()
    var inputTextPart: InputTextPart = InputTextPart.PRE_TOC
    var skip = false
    var putBackLine: String? = null

    val outText: MutableList<String> get() = when (inputTextPart) {
        InputTextPart.PRE_TOC -> preTocText
        InputTextPart.POST_TOC -> postTocText
        else -> throw IllegalStateException("Wrong state: $inputTextPart")
    }

    override fun readLine(): String? {
        putBackLine?.let {
            putBackLine = null
            return it
        }
        val line = super.readLine() ?: return null
        inText += line
        if (!skip && inputTextPart != InputTextPart.TOC)
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
            if (directive(skipLine, inputFileType.lineStartIndex(skipLine)) != null) {
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

fun KnitContext.withInputTextReader(file: File, type: InputFileType, block: InputTextReader.() -> Unit): InputTextReader? =
    withLineNumberReader(file, { r -> InputTextReader(type, r) }, block)

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
        val diff = computeLinesDiff(oldLines, outLines)
        "is not up-to-date, $diff"
    }

private fun KnitContext.writeLines(file: File, lines: List<String>) {
    val lineSep = if (file.exists()) {
        file.bufferedReader().use {
            it.firstLineSeparator()
        } ?: lineSeparator
    } else {
        lineSeparator
    }
    log.info("Writing $file ...")
    file.parentFile?.mkdirs()
    file.bufferedWriter().use { out ->
        lines.forEach { out.write("$it$lineSep") }
    }
}

fun KnitContext.findMultiModuleRoot(): String? =
        dokkaMultiModuleRoot.takeIf { rootDir.resolve(it).exists() }

fun KnitContext.findModuleRoot(isRoot: Boolean, name: String): String =
    moduleRoots
        .map { if (isRoot) it else "$it/$name" }
        .firstOrNull { dir -> moduleMarkers.any { (rootDir / "$dir/$it").exists() } }
        ?: throw IllegalArgumentException("${if (isRoot) "Root module" else "Module"} $name is not found in any of the module root dirs")
