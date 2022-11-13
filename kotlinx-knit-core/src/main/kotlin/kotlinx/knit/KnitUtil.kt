/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*

internal fun <T : LineNumberReader> KnitContext.withLineNumberReader(file: File, factory: (Reader) -> T, block: T.() -> Unit): T? {
    val reader = factory(file.reader())
    reader.use {
        try {
            it.block()
        } catch (e: Exception) {
            log.error("ERROR: $file: ${it.lineNumber}: ${e.message}", e)
            return null
        }
    }
    return reader
}

internal operator fun File.div(path: String): File = File(this, path.replace("/", File.separator))

internal fun Reader.firstLineSeparator(): String? {
    val n = '\n'.toInt()
    val r = '\r'.toInt()
    while (true) {
        val current = read()
        if (current == -1) {
            return null
        } else if (current == n || current == r) {
            var result = current.toChar().toString()
            val next = read()
            if (current == r && next == n) {
                result += next.toChar().toString()
            }
            return result
        }
    }
}
