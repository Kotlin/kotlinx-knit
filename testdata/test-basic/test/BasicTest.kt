// This file was automatically generated from test-basic.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class BasicTest {
    @Test
    fun testExampleBasic01() {
        captureOutput("ExampleBasic01") { com.example.exampleBasic01.main() }.verifyOutputLines(
            "Hello, world!"
        )
    }
}
