package com.palmerodev.fww

import com.palmerodev.fww.detection.FlutterContextAnalyzer
import com.palmerodev.fww.detection.FlutterWidgetDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlutterWidgetDetectorTest {

    @Test
    fun `returns null for non-dart files`() {
        val detected = FlutterWidgetDetector.detect("foo.kt", "Text('Hi')", 2)
        assertNull(detected)
    }

    @Test
    fun `detects innermost widget under cursor`() {
        val text = "Row(children: [Text('Hi')])"
        val cursor = text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)
        assertNotNull(detected)
        assertEquals("Text", detected!!.name)
    }

    @Test
    fun `parent of widget inside Row is Row`() {
        val text = "Row(children: [Text('Hi')])"
        val cursor = text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)!!
        assertEquals("Row", detected.parentWidgetName)
        val ctx = FlutterContextAnalyzer.analyze(detected)
        assertTrue(ctx.isDirectChildOfFlex)
    }

    @Test
    fun `parent of widget inside Padding is Padding`() {
        val text = "Padding(padding: EdgeInsets.all(8), child: Text('Hi'))"
        val cursor = text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)!!
        assertEquals("Padding", detected.parentWidgetName)
        val ctx = FlutterContextAnalyzer.analyze(detected)
        assertTrue(!ctx.isDirectChildOfFlex)
    }

    @Test
    fun `ignores parens inside strings`() {
        val text = "Text('a (b) c')"
        val cursor = 3
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)!!
        assertEquals("Text", detected.name)
        assertNull(detected.parentWidgetName)
    }

    @Test
    fun `handles multiline widget and reports full range text`() {
        val text = """
            Column(
              children: [
                Text('Hello'),
              ],
            )
        """.trimIndent()
        val cursor = text.indexOf("Hello")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)!!
        assertEquals("Text", detected.name)
        assertEquals("Column", detected.parentWidgetName)
    }

    @Test
    fun `ancestors chain lists nearest parent first`() {
        val text = "Column(children: [Row(children: [Text('Hi')])])"
        val cursor = text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect("main.dart", text, cursor)!!
        assertEquals("Row", detected.parentWidgetName)
        assertEquals(listOf("Row", "Column"), detected.ancestors)
    }

    @Test
    fun `top level widget has no parent`() {
        val text = "Text('Hi')"
        val detected = FlutterWidgetDetector.detect("main.dart", text, 2)!!
        assertEquals("Text", detected.name)
        assertNull(detected.parentWidgetName)
    }
}
