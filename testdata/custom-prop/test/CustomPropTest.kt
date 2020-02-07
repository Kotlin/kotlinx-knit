// Copyright 2016-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license.
// This file was automatically generated from custom-prop.in.md by Knit tool. Do not edit.
package com.example.test

import org.junit.Test
import kotlinx.knit.test.*
import java.util.*

class CustomPropTest {
    @Test
    fun `testExampleCustom01`() {
        com.example.exampleCustom01.result().verifyOutputLines(
            "Hello",
            "World"
        )
    }
}
