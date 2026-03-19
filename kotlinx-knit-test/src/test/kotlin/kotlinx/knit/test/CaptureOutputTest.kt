package kotlinx.knit.test

import kotlin.test.Test
import kotlin.test.assertEquals

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
