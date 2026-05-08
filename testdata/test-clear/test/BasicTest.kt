// This file was automatically generated from test-basic.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class ClearTest {
    @Test
    fun testExampleBasic01() {
        captureOutput("ExampleClear01") { com.example.exampleClear01.main() }.verifyOutputLines(
            "Hello, world!"
        )
    }
}
