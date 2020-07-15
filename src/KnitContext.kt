/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import java.io.*
import java.util.*

open class KnitGlobals(
    val siteRoot: String?, // only needed for INDEX directive
    val moduleRoots: List<String>,
    val moduleMarkers: List<String>,
    val moduleDocs: String
) {
    constructor(props: KnitProps) : this(
        siteRoot = props.getValue("site.root"),
        moduleRoots = props.getValue("module.roots").split(","),
        moduleMarkers = props.getValue("module.markers").split(","),
        moduleDocs = props.getValue("module.docs")
    )

    constructor(globals: KnitGlobals) : this(
        siteRoot = globals.siteRoot,
        moduleRoots = globals.moduleRoots,
        moduleMarkers = globals.moduleMarkers,
        moduleDocs = globals.moduleDocs
    )
}

class KnitContext(
    val log: KnitLog,
    // the global configuration from knit { ... } DSL
    globals: KnitGlobals,
    // files to process
    files: Collection<File>,
    val rootDir: File,
    val check: Boolean
) : KnitGlobals(globals) {
    // state
    val tocRefMap = HashMap<File, List<TocRef>>()
    val fileSet = HashSet(files)
    val fileQueue = ArrayDeque(files)
    val apiIndexCache = HashMap<ApiIndexKey, Map<String, List<String>>>()
    val propsCache = HashMap<File, KnitProps>()
}
