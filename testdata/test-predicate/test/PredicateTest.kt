// This file was automatically generated from test-predicate.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*

class PredicateTest {
    @Test
    fun testExamplePredicate01() {
        captureOutput("ExamplePredicate01") { com.example.examplePredicate01.main() }.also { lines ->
            check(lines.single().toInt() in 1..100)
        }
    }
}
