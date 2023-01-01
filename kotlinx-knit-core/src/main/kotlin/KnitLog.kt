/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.slf4j.*

sealed class KnitLog {
    var hasWarningOrError = false
    var nOutdated = 0

    abstract fun debug(s: String)
    abstract fun info(s: String)
    abstract fun warn(s: String)
    abstract fun error(s: String, e: Exception)

    fun outdated(s: String) {
        warn(s)
        nOutdated++
    }
}

class ConsoleLog : KnitLog() {
    override fun debug(s: String) {
        // no debug output to console
    }

    override fun info(s: String) {
        println(s)
    }

    override fun warn(s: String) {
        println(s)
        hasWarningOrError = true
    }

    override fun error(s: String, e: Exception) {
        println(s)
        e.printStackTrace(System.out)
        hasWarningOrError = true
    }
}

class LoggerLog : KnitLog() {
    private val logger: Logger = LoggerFactory.getLogger("knit")

    override fun debug(s: String) {
        logger.debug(s)
    }

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
