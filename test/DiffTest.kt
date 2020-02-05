/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit

import org.junit.Test
import kotlin.random.*
import kotlin.test.*

class DiffTest {
    @Test
    fun testDiffSame() {
        assertEquals(
            "",
            formatDiff(
                listOf(),
                listOf()
            )
        )
        assertEquals(
            "",
            formatDiff(
                listOf("a", "b", "c"),
                listOf("a", "b", "c")
            )
        )
    }

    @Test
    fun testDiffInsert() {
        assertEquals(
            """
            3a4
            > d
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c"),
                listOf("a", "b", "c", "d")
            )
        )
        assertEquals(
            """
            0a1
            > _
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c"),
                listOf("_", "a", "b", "c")
            )
        )
        assertEquals(
            """
            0a1,3
            > 1
            > 2
            > 3
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c"),
                listOf("1", "2", "3", "a", "b", "c")
            )
        )
        assertEquals(
            """
            1a2
            > 1
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c"),
                listOf("a", "1", "b", "c")
            )
        )
        assertEquals(
            """
            1a2
            > 1
            2a4
            > 2
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c"),
                listOf("a", "1", "b", "2", "c")
            )
        )
    }

    @Test
    fun testDiffDelete() {
        assertEquals(
            """
            4d3
            < d
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c", "d"),
                listOf("a", "b", "c")
            )
        )
        assertEquals(
            """
            2,3d1
            < b
            < c
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c", "d"),
                listOf("a", "d")
            )
        )
        assertEquals(
            """
            2d1
            < b
            4d2
            < d
            """.trimIndent(),
            formatDiff(
                listOf("a", "b", "c", "d", "e"),
                listOf("a", "c", "e")
            )
        )
    }

    @Test
    fun testDiffChange() {
        assertEquals(
            """
            1,2c1,2
            < a
            < b
            ---
            > c
            > d
            """.trimIndent(),
            formatDiff(
                listOf("a", "b"),
                listOf("c", "d")
            )
        )
    }

    @Test
    fun testDiffComplex() {
        // from https://en.wikipedia.org/wiki/Diff
        val oldLines = """
            This part of the
            document has stayed the
            same from version to
            version.  It shouldn't
            be shown if it doesn't
            change.  Otherwise, that
            would not be helping to
            compress the size of the
            changes.

            This paragraph contains
            text that is outdated.
            It will be deleted in the
            near future.

            It is important to spell
            check this dokument. On
            the other hand, a
            misspelled word isn't
            the end of the world.
            Nothing in the rest of
            this paragraph needs to
            be changed. Things can
            be added after it.
        """.trimIndent().lines()
        val newLines = """
            This is an important
            notice! It should
            therefore be located at
            the beginning of this
            document!

            This part of the
            document has stayed the
            same from version to
            version.  It shouldn't
            be shown if it doesn't
            change.  Otherwise, that
            would not be helping to
            compress the size of the
            changes.

            It is important to spell
            check this document. On
            the other hand, a
            misspelled word isn't
            the end of the world.
            Nothing in the rest of
            this paragraph needs to
            be changed. Things can
            be added after it.

            This paragraph contains
            important new additions
            to this document.
        """.trimIndent().lines()
        val expectedDiff = """
            0a1,6
            > This is an important
            > notice! It should
            > therefore be located at
            > the beginning of this
            > document!
            > 
            11,15d16
            < This paragraph contains
            < text that is outdated.
            < It will be deleted in the
            < near future.
            < 
            17c18
            < check this dokument. On
            ---
            > check this document. On
            24a26,29
            > 
            > This paragraph contains
            > important new additions
            > to this document.
        """.trimIndent()
        assertEquals(expectedDiff, formatDiff(oldLines, newLines))
    }

    @Test
    fun testBigDiffNull() {
        val n = 100_000
        val k = 10
        val rnd = Random(1)
        fun rndLines() = List(n) {
            buildString {
                repeat(k) { append('a' + rnd.nextInt(26)) }
            }
        }
        val oldLines = rndLines()
        val newLines = rndLines()
        assertNull(formatDiff(oldLines, newLines))
    }
}