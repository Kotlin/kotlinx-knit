/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*
import java.util.*

class KnitContext(
    val log: KnitLog,
    // the global configuration from knit { ... } DSL
    val siteRoot: String?, // only needed for INDEX directive
    val moduleRoots: List<String>,
    val moduleMarkers: List<String>,
    val moduleDocs: String,
    // files to process
    files: Collection<File>,
    val rootDir: File,
    val check: Boolean
) {
    // state
    val tocRefMap = HashMap<File, List<TocRef>>()
    val fileSet = HashSet(files)
    val fileQueue = ArrayDeque(files)
    val apiIndexCache = HashMap<ApiIndexKey, Map<String, List<String>>>()
    val propsCache = HashMap<File, KnitProps>()
}
