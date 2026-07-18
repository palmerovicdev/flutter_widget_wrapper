package com.palmerodev.fww

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.lang.dart.psi.DartFile
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.detection.MultiWidgetSelectionDetector

/**
 * Verifies that detection goes through the Dart PSI when the Dart plugin parses the file.
 * Snippets are wrapped in a function body so the parser builds real call expressions (a bare
 * top-level `Text(...)` is recovered as an incomplete declaration, not a call).
 */
class PsiFlutterWidgetDetectorTest : BasePlatformTestCase() {

    private fun dart(body: String): String = "void f() {\n$body\n}"

    fun `test configureByText yields a DartFile with call expressions`() {
        val file = myFixture.configureByText("main.dart", dart("Text('hi');"))
        assertTrue("Expected DartFile, got ${file::class.java.name}", file is DartFile)
        assertNotNull(
            "Expected a DartCallExpression in the tree",
            file.findElementAt(file.text.indexOf("Text"))?.let { leaf ->
                generateSequence(leaf) { it.parent }.firstOrNull {
                    it.javaClass.simpleName.contains("CallExpression")
                }
            },
        )
    }

    fun `test detects innermost widget via PSI`() {
        myFixture.configureByText("main.dart", dart("Row(children: [Text('Hi')]);"))
        val offset = myFixture.file.text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect(myFixture.file, offset)
        assertNotNull(detected)
        assertEquals("Text", detected!!.name)
        assertEquals("Row", detected.parentWidgetName)
    }

    fun `test ignores non-widget constructors via PSI`() {
        myFixture.configureByText(
            "main.dart",
            dart("Padding(padding: EdgeInsets.all(8), child: Text('Hi'));"),
        )
        val offset = myFixture.file.text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect(myFixture.file, offset)
        assertNotNull(detected)
        assertEquals("Text", detected!!.name)
        assertEquals("Padding", detected.parentWidgetName)
        assertFalse(detected.ancestors.contains("EdgeInsets"))
    }

    fun `test ancestors chain via PSI`() {
        myFixture.configureByText("main.dart", dart("Column(children: [Row(children: [Text('Hi')])]);"))
        val offset = myFixture.file.text.indexOf("'Hi'")
        val detected = FlutterWidgetDetector.detect(myFixture.file, offset)!!
        assertEquals(listOf("Row", "Column"), detected.ancestors)
    }

    fun `test named constructor uses the class name`() {
        myFixture.configureByText("main.dart", dart("Center(child: ListView.builder());"))
        val offset = myFixture.file.text.indexOf("ListView")
        val detected = FlutterWidgetDetector.detect(myFixture.file, offset)
        assertNotNull(detected)
        assertEquals("ListView", detected!!.name)
        assertEquals("Center", detected.parentWidgetName)
    }

    fun `test multi widget selection via PSI`() {
        myFixture.configureByText("main.dart", dart("Column(children: [Text('A'), Text('B')]);"))
        val text = myFixture.file.text
        val start = text.indexOf("Text('A')")
        val end = text.indexOf("Text('B')") + "Text('B')".length
        val result = MultiWidgetSelectionDetector.analyze(myFixture.file, start, end)
        assertNotNull(result)
        assertEquals("Column", result!!.parentWidgetName)
        assertEquals(2, result.elements.size)
    }
}
