/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.slf4j.*

var log: KnitLog = object : KnitLog {
    val logger: Logger by lazy { LoggerFactory.getLogger("knit") }
    override fun info(s: String) = logger.info(s)
    override fun warn(s: String) = logger.warn(s)
    override fun error(s: String, e: Exception) = logger.error(s)
}

interface KnitLog {
    fun info(s: String)
    fun warn(s: String)
    fun error(s: String, e: Exception)
}

class SimpleLog : KnitLog {
    override fun info(s: String) = println(s)
    override fun warn(s: String) = println(s)
    override fun error(s: String, e: Exception) = println(s)
}