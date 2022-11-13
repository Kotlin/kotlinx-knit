/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import kotlinx.knit.test.*
import java.nio.file.*
import kotlin.streams.*
import kotlin.test.*

val TEST_ROOT_DIR: Path = Paths.get("build", "testdata")

sealed class FileRef(val path: String) {
    class Copy(path: String) : FileRef(path)
    class Expect(path: String) : FileRef(path)

    override fun toString(): String = "FileRef.${this::class.java.simpleName}(\"$path\")"
}

fun verifyTestData(
    testName: String,
    inFile: String,
    outFile: String,
    propFile: String?,
    vararg refs: FileRef
) {
    // clean directory
    val dir = TEST_ROOT_DIR.resolve(testName)
    deleteDirectory(dir)
    Files.createDirectories(dir)
    // copy files
    val inPath = Resources.getFile(inFile)
    val targetPath = dir.resolve(inPath.fileName)
    Files.copy(inPath, targetPath)

    if (propFile != null) Files.copy(Resources.getFile(propFile), dir.resolve("knit.properties"))

    refs.filterIsInstance<FileRef.Copy>().forEach { ref ->
        val path = Paths.get(ref.path)
        Files.copy(path, dir.resolve(path.fileName))
    }
    // run knit
    val context = createDefaultContext(listOf(targetPath.toFile()), dir.toFile())
    assertTrue(context.process(), "Knit failed, see log")
    // verify resulting files
    assertSameFile(Resources.getFile(outFile), targetPath)
    refs.filterIsInstance<FileRef.Expect>().forEach { ref ->
        val expected = Resources.getFile(ref.path)
        val actual = targetPath.parent.resolve(inPath.parent.relativize(expected))
        assertSameFile(expected, actual)
    }
}

fun assertSameFile(expected: Path, actual: Path) {
    assertTrue(Files.exists(actual), "File $actual does not exist, but is expected to match $expected")
    val expectedLines = Files.readAllLines(expected)
    val actualLines = Files.readAllLines(actual)
    if (actualLines != expectedLines) {
        val diff = computeLinesDiff(expectedLines, actualLines)
        error("Actual file in $actual and not the same as expected in $expected, $diff")
    }
}

private fun deleteDirectory(dir: Path) {
    if (!Files.exists(dir)) return
    Files.walk(dir).use {
        it.asSequence().sortedDescending().forEach { Files.delete(it) }
    }
}

private object Resources {
    fun getFile(path: String): Path {
        return Paths.get(Resources::class.java.classLoader.getResource(path)!!.toURI())
    }

}
