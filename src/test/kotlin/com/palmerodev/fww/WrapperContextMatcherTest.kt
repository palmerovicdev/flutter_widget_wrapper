package com.palmerodev.fww

import com.palmerodev.fww.model.FlutterWidgetContext
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperContextMatcher
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WrapperContextMatcherTest {

    private fun ctx(
        parent: String? = null,
        ancestors: List<String> = emptyList(),
    ): FlutterWidgetContext = FlutterWidgetContext(
        widgetName = "Text",
        parentWidgetName = parent,
        ancestors = ancestors,
        isDirectChildOfFlex = parent in setOf("Row", "Column", "Flex"),
        isInsideStack = "Stack" in ancestors,
    )

    private val safeArea = WidgetWrapper(
        name = "SafeArea",
        template = listOf("SafeArea(", "  child: \${widget},", ")"),
    )

    private val expanded = WidgetWrapper(
        name = "Expanded",
        template = listOf("Expanded(", "  child: \${widget},", ")"),
        allowedParents = listOf("Row", "Column", "Flex"),
        requiresDirectParent = true,
    )

    @Test
    fun `SafeArea appears on any widget with no parent`() {
        assertTrue(WrapperContextMatcher.matches(safeArea, ctx()))
    }

    @Test
    fun `SafeArea is hidden when parent is already SafeArea`() {
        assertFalse(WrapperContextMatcher.matches(safeArea, ctx(parent = "SafeArea")))
    }

    @Test
    fun `Expanded shows inside Row`() {
        assertTrue(WrapperContextMatcher.matches(expanded, ctx(parent = "Row")))
    }

    @Test
    fun `Expanded is hidden inside Padding`() {
        assertFalse(WrapperContextMatcher.matches(expanded, ctx(parent = "Padding")))
    }

    @Test
    fun `Expanded is hidden when parent is Column but requiresDirectParent and there is no parent`() {
        assertFalse(WrapperContextMatcher.matches(expanded, ctx(parent = null)))
    }

    @Test
    fun `disabled wrapper never matches`() {
        val disabled = safeArea.copy(enabled = false)
        assertFalse(WrapperContextMatcher.matches(disabled, ctx()))
    }

    @Test
    fun `disallowedParents blocks match`() {
        val noScroll = safeArea.copy(disallowedParents = listOf("SingleChildScrollView"))
        assertFalse(
            WrapperContextMatcher.matches(
                noScroll,
                ctx(parent = "SingleChildScrollView"),
            )
        )
    }
}
