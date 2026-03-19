package kotlinx.knit

import org.junit.Test
import kotlin.test.assertEquals

class KnitUtilTest {
    @Test
    fun testFirstLineSeparator() {
        mapOf(
            "" to null,
            "\n" to "\n",
            "\r" to "\r",
            "\r\n" to "\r\n",
            "\n\r" to "\n",
            "abc" to null,
            "abc\n" to "\n",
            "abc\r\n" to "\r\n",
            "abc\nxyz\r" to "\n",
            "abc\r\nxyz\n\r" to "\r\n"
        ).forEach { (input, expected) ->
            assertEquals(expected, input.reader().firstLineSeparator())
        }
    }
}
