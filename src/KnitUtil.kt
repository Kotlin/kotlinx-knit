/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*

fun <T : LineNumberReader> File.withLineNumberReader(factory: (Reader) -> T, block: T.() -> Unit): T? {
    val reader = factory(reader())
    reader.use {
        try {
            it.block()
        } catch (e: Exception) {
            println("ERROR: ${this@withLineNumberReader}: ${it.lineNumber}: ${e.message}")
            return null
        }
    }
    return reader
}

operator fun File.div(path: String): File = File(this, path.replace("/", File.separator))