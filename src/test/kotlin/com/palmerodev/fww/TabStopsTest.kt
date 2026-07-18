package com.palmerodev.fww

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.TabStops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabStopsTest {

    private fun wrapper(vararg lines: String) = WidgetWrapper(name = "W", template = lines.toList())

    @Test
    fun `hasTabStops is false when only the widget placeholder is present`() {
        assertFalse(TabStops.hasTabStops(wrapper("SafeArea(", "  child: \${widget},", ")")))
    }

    @Test
    fun `hasTabStops detects a named tab-stop`() {
        assertTrue(
            TabStops.hasTabStops(
                wrapper("Opacity(", "  opacity: \${opacity:0.5},", "  child: \${widget},", ")"),
            ),
        )
    }

    @Test
    fun `hasTabStops detects the end marker`() {
        assertTrue(
            TabStops.hasTabStops(
                wrapper("GestureDetector(", "  onTap: () {\${end}},", "  child: \${widget},", ")"),
            ),
        )
    }

    @Test
    fun `tokenize splits literal, variable and trailing literal`() {
        assertEquals(
            listOf(
                TabStops.Token.Literal("  opacity: "),
                TabStops.Token.Variable("opacity", "0.5"),
                TabStops.Token.Literal(","),
            ),
            TabStops.tokenize("  opacity: \${opacity:0.5},"),
        )
    }

    @Test
    fun `tokenize recognizes the widget and end markers`() {
        assertEquals(
            listOf(
                TabStops.Token.Literal("  child: "),
                TabStops.Token.Widget,
                TabStops.Token.Literal(","),
            ),
            TabStops.tokenize("  child: \${widget},"),
        )
        assertEquals(
            listOf(
                TabStops.Token.Literal("  onTap: () {"),
                TabStops.Token.End,
                TabStops.Token.Literal("},"),
            ),
            TabStops.tokenize("  onTap: () {\${end}},"),
        )
    }

    @Test
    fun `tokenize treats a marker without default as an empty-default variable`() {
        assertEquals(
            listOf(TabStops.Token.Variable("count", "")),
            TabStops.tokenize("\${count}"),
        )
    }

    @Test
    fun `stripToDefaults renders defaults, drops end, and keeps widget`() {
        assertEquals("  opacity: 0.5,", TabStops.stripToDefaults("  opacity: \${opacity:0.5},"))
        assertEquals("  onTap: () {},", TabStops.stripToDefaults("  onTap: () {\${end}},"))
        assertEquals("  child: \${widget},", TabStops.stripToDefaults("  child: \${widget},"))
    }
}
