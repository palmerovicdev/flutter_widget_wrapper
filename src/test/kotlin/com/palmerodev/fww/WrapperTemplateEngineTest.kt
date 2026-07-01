package com.palmerodev.fww

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class WrapperTemplateEngineTest {

    @Test
    fun `replaces placeholder for simple wrapper`() {
        val wrapper = WidgetWrapper(
            name = "SafeArea",
            template = listOf(
                "SafeArea(",
                "  child: \${widget},",
                ")",
            ),
        )
        val result = WrapperTemplateEngine.apply(wrapper, "Text('Hello')", "")
        val expected = """
            SafeArea(
              child: Text('Hello'),
            )
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `injects base indent on every wrapper line except first`() {
        val wrapper = WidgetWrapper(
            name = "SafeArea",
            template = listOf(
                "SafeArea(",
                "  child: \${widget},",
                ")",
            ),
        )
        val result = WrapperTemplateEngine.apply(wrapper, "Text('Hello')", "    ")
        val expected = "SafeArea(\n      child: Text('Hello'),\n    )"
        assertEquals(expected, result)
    }

    @Test
    fun `reindents multiline widget text preserving its inner structure`() {
        val wrapper = WidgetWrapper(
            name = "SafeArea",
            template = listOf(
                "SafeArea(",
                "  child: \${widget},",
                ")",
            ),
        )
        val widgetText = "Container(\n  color: Colors.red,\n  child: Text('Hi'),\n)"
        val result = WrapperTemplateEngine.apply(wrapper, widgetText, "")
        val expected = """
            SafeArea(
              child: Container(
                color: Colors.red,
                child: Text('Hi'),
              ),
            )
        """.trimIndent()
        assertEquals(expected, result)
    }

    @Test
    fun `stack template moves child into children list`() {
        val wrapper = WidgetWrapper(
            name = "Stack",
            template = listOf(
                "Stack(",
                "  children: [",
                "    \${widget},",
                "  ],",
                ")",
            ),
        )
        val result = WrapperTemplateEngine.apply(wrapper, "Text('Hello')", "")
        val expected = """
            Stack(
              children: [
                Text('Hello'),
              ],
            )
        """.trimIndent()
        assertEquals(expected, result)
    }
}
