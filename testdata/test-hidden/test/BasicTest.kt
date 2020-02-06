// This file was automatically generated from test-hidden.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class BasicTest {
    @Test
    fun testExampleHidden01() {
        captureOutput("ExampleHidden01") { com.example.exampleHidden01.main() }.verifyOutputLines(
            "Hello, world!"
        )
    }
}
