/*
 * Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
 */

package kotlinx.knit.test

import kotlin.test.*

class CaptureOutputTest {
    @Test
    fun testStdout() {
        val lines = captureOutput("Stdout") {
            println("Line1")
            println("Line2")
        }
        assertEquals(listOf("Line1", "Line2"), lines)
    }

    @Test
    fun testStderr() {
        val lines = captureOutput("Stderr") {
            System.err.println("Line1")
            System.err.println("Line2")
        }
        assertEquals(listOf("Line1", "Line2"), lines)
    }
}