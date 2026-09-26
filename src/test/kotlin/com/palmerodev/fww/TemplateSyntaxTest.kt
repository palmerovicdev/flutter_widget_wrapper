package com.palmerodev.fww

import com.palmerodev.fww.model.ChildKind
import com.palmerodev.fww.model.FlutterWidgetContext
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.TabStops
import com.palmerodev.fww.wrappers.TemplateLint
import com.palmerodev.fww.wrappers.WrapperContextMatcher
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure (no platform) coverage for choice tab-stops, template lint and slot rules. */
class TemplateSyntaxTest {

    @Test
    fun `choice tab-stop defaults to the first option`() {
        val token = TabStops.tokenize("\${curve:Curves.easeIn|Curves.linear}").single() as TabStops.Token.Variable
        assertEquals("Curves.easeIn", token.default)
        assertEquals(listOf("Curves.easeIn", "Curves.linear"), token.options)
        assertEquals("curve: Curves.easeIn", TabStops.stripToDefaults("curve: \${curve:Curves.easeIn|Curves.linear}"))
    }

    @Test
    fun `dart or operator is not split into choices`() {
        val token = TabStops.tokenize("\${cond:a || b}").single() as TabStops.Token.Variable
        assertEquals("a || b", token.default)
        assertTrue(token.options.isEmpty())
    }

    @Test
    fun `lint reports unclosed markers, reserved defaults and unbalanced brackets`() {
        assertEquals(
            listOf(TemplateLint.Problem.MalformedMarker(1)),
            TemplateLint.check(listOf("Foo(child: \${widget)")).filterIsInstance<TemplateLint.Problem.MalformedMarker>(),
        )
        assertTrue(
            TemplateLint.check(listOf("Foo(child: \${widget:x})"))
                .contains(TemplateLint.Problem.ReservedWithDefault("widget")),
        )
        assertTrue(
            TemplateLint.check(listOf("Foo(", "  child: \${widget},"))
                .contains(TemplateLint.Problem.Unbalanced('(')),
        )
        assertTrue(TemplateLint.check(listOf("Foo(", "  label: '(',", "  child: \${widget},", ")")).isEmpty())
    }

    private fun ctx(widget: String, slot: String?) = FlutterWidgetContext(
        widgetName = widget,
        parentWidgetName = "CustomScrollView",
        ancestors = listOf("CustomScrollView"),
        isDirectChildOfFlex = false,
        isInsideStack = false,
        slot = slot,
    )

    private val adapter = WidgetWrapper(
        "SliverToBoxAdapter", listOf("SliverToBoxAdapter(child: \${widget})"),
        allowedSlots = listOf("slivers", "sliver"), childKind = ChildKind.BOX,
    )
    private val safeArea = WidgetWrapper("SafeArea", listOf("SafeArea(child: \${widget})"))

    @Test
    fun `sliver slots only offer sliver-aware wrappers`() {
        assertTrue(WrapperContextMatcher.matches(adapter, ctx("Text", "slivers")))
        assertFalse(WrapperContextMatcher.matches(safeArea, ctx("Text", "slivers")))
        assertFalse("A sliver is not a box", WrapperContextMatcher.matches(adapter, ctx("SliverList", "slivers")))
        assertFalse("Slot-restricted wrappers stay out of normal slots", WrapperContextMatcher.matches(adapter, ctx("Text", "child")))
        assertTrue(WrapperContextMatcher.matches(safeArea, ctx("Text", "child")))
    }

    @Test
    fun `slots and child kind round-trip through json`() {
        val parsed = WrapperJsonCodec.parseList(WrapperJsonCodec.encodeList(listOf(adapter))).single()
        assertEquals(listOf("slivers", "sliver"), parsed.allowedSlots)
        assertEquals(ChildKind.BOX, parsed.childKind)
    }
}
