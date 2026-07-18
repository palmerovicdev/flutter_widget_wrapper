package com.palmerodev.fww

import com.palmerodev.fww.detection.MultiWidgetSelectionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MultiWidgetSelectionDetectorTest {

    private fun analyze(text: String, from: String, to: String): MultiWidgetSelectionDetector.Result? {
        val start = text.indexOf(from)
        val end = text.indexOf(to) + to.length
        return MultiWidgetSelectionDetector.analyze(text, start, end)
    }

    @Test
    fun `two siblings in a Column are detected with parent Column`() {
        val text = """
            Column(
              children: [
                Text('A'),
                Text('B'),
              ],
            )
        """.trimIndent()
        val r = analyze(text, "Text('A')", "Text('B'),")
        assertNotNull(r)
        assertEquals(listOf("Text('A')", "Text('B')"), r!!.elements)
        assertEquals("Column", r.parentWidgetName)
    }

    @Test
    fun `three siblings in a Row are detected`() {
        val text = "Row(children: [Icon(a), Text('x'), Spacer()])"
        val r = analyze(text, "Icon(a)", "Spacer()")
        assertNotNull(r)
        assertEquals(3, r!!.elements.size)
        assertEquals("Row", r.parentWidgetName)
    }

    @Test
    fun `single widget selection is rejected`() {
        val text = "Column(children: [Text('A'), Text('B')])"
        val r = analyze(text, "Text('A')", "Text('A')")
        assertNull(r)
    }

    @Test
    fun `siblings inside a non-flex list are rejected`() {
        val text = "ListView(children: [Text('A'), Text('B')])"
        val r = analyze(text, "Text('A')", "Text('B')")
        assertNull(r)
    }

    @Test
    fun `children of a Stack are rejected`() {
        val text = "Stack(children: [Positioned(child: A()), Text('B')])"
        val r = analyze(text, "Positioned(child: A())", "Text('B')")
        assertNull(r)
    }

    @Test
    fun `nested siblings resolve to the innermost flex parent`() {
        val text = "Column(children: [Row(children: [Text('A'), Text('B')])])"
        val r = analyze(text, "Text('A')", "Text('B')")
        assertNotNull(r)
        assertEquals("Row", r!!.parentWidgetName)
    }

    @Test
    fun `commas inside child arguments do not oversplit`() {
        val text = "Column(children: [SizedBox(width: 1, height: 2), Text('B')])"
        val r = analyze(text, "SizedBox(width: 1, height: 2)", "Text('B')")
        assertNotNull(r)
        assertEquals(2, r!!.elements.size)
        assertEquals("SizedBox(width: 1, height: 2)", r.elements.first())
    }

    @Test
    fun `non-children named list under Row is rejected`() {
        val text = "Row(foo: [Text('A'), Text('B')])"
        val r = analyze(text, "Text('A')", "Text('B')")
        assertNull(r)
    }

    @Test
    fun `Duration siblings are rejected as non-widgets`() {
        val text = "Column(children: [Duration(seconds: 1), Text('B')])"
        val r = analyze(text, "Duration(seconds: 1)", "Text('B')")
        assertNull(r)
    }
}
