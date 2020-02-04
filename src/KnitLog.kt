/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.slf4j.*

sealed class KnitLog {
    var hasWarningOrError = false
    abstract fun info(s: String)
    abstract fun warn(s: String)
    abstract fun error(s: String, e: Exception)
}

class ConsoleLog : KnitLog() {
    override fun info(s: String) {
        println(s)
    }

    override fun warn(s: String) {
        println(s)
        hasWarningOrError = true
    }

    override fun error(s: String, e: Exception) = warn(s)
}

class LoggerLog : KnitLog() {
    private val logger: Logger by lazy { LoggerFactory.getLogger("knit") }

    override fun info(s: String) {
        logger.info(s)
    }

    override fun warn(s: String) {
        logger.warn(s)
        hasWarningOrError = true
    }

    override fun error(s: String, e: Exception) {
        logger.error(s)
        hasWarningOrError = true
    }
}