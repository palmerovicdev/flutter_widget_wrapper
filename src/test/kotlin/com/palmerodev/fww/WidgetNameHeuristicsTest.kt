package com.palmerodev.fww

import com.palmerodev.fww.detection.WidgetNameHeuristics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetNameHeuristicsTest {

    @Test
    fun `simple widget name is accepted`() {
        assertTrue(WidgetNameHeuristics.isWidgetName("Text"))
        assertEquals("Text", WidgetNameHeuristics.classNameFromReference("Text"))
    }

    @Test
    fun `named constructor builder is promoted`() {
        assertEquals("ListView", WidgetNameHeuristics.classNameFromReference("ListView.builder"))
        assertTrue(WidgetNameHeuristics.isPromotableNamedMember("builder"))
    }

    @Test
    fun `of static is not promoted`() {
        assertNull(WidgetNameHeuristics.classNameFromReference("Theme.of"))
        assertFalse(WidgetNameHeuristics.isPromotableNamedMember("of"))
    }

    @Test
    fun `generate static is not promoted`() {
        assertNull(WidgetNameHeuristics.classNameFromReference("List.generate"))
    }

    @Test
    fun `non-widget types are rejected`() {
        assertFalse(WidgetNameHeuristics.isWidgetName("Duration"))
        assertFalse(WidgetNameHeuristics.isWidgetName("Future"))
        assertFalse(WidgetNameHeuristics.isWidgetName("ValueNotifier"))
    }

    @Test
    fun `Image asset remains a widget reference`() {
        assertEquals("Image", WidgetNameHeuristics.classNameFromReference("Image.asset"))
    }
}
