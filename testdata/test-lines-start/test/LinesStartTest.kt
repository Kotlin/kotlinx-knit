// This file was automatically generated from test-lines-start.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class LinesStartTest {
    @Test
    fun testExampleLinesStart01() {
        captureOutput("ExampleLinesStart01") { com.example.exampleLinesStart01.main() }.verifyOutputLinesStart(
            "Exception in thread \"main\" java.lang.IllegalStateException: The check has failed"
        )
    }
}
