/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*

fun <T : LineNumberReader> File.withLineNumberReader(factory: (Reader) -> T, block: T.() -> Unit): T? {
    val file = this
    val reader = factory(reader())
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

operator fun File.div(path: String): File = File(this, path.replace("/", File.separator))