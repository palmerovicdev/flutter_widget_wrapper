package com.palmerodev.fww

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.intention.WrapSelectionIntention
import com.palmerodev.fww.intention.WrapWithChooserIntention
import com.palmerodev.fww.intention.WrapWithWidgetIntention
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.surround.WidgetSurroundDescriptor
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperTemplateEngine

/** 1.4 coverage: grouped chooser, caret-on-name, priority, Surround With, multi-select wrappers. */
class WrapChooserAndSurroundTest : BasePlatformTestCase() {

    private fun dart(body: String): String = "void f() {\n$body\n}"

    private val settings get() = FlutterWrapperSettings.getInstance()

    override fun setUp() {
        super.setUp()
        resetSettings()
    }

    override fun tearDown() {
        try {
            resetSettings()
        } finally {
            super.tearDown()
        }
    }

    private fun resetSettings() {
        settings.customWrappersJson = ""
        settings.disabledBuiltInNames = mutableSetOf()
        settings.groupWrappers = false
        settings.caretOnNameOnly = false
    }

    private fun caretAt(marker: String, delta: Int = 1) {
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(marker) + delta)
    }

    private fun available(intention: IntentionAction) =
        intention.isAvailable(project, myFixture.editor, myFixture.file)

    fun `test grouped mode hides per-wrapper entries and shows the chooser`() {
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        caretAt("Text")
        assertTrue(available(WrapWithWidgetIntention("Align")))
        assertFalse(available(WrapWithChooserIntention()))

        settings.groupWrappers = true
        assertFalse("Per-wrapper entries are hidden when grouped", available(WrapWithWidgetIntention("Align")))
        val chooser = WrapWithChooserIntention()
        assertTrue(available(chooser))
        assertTrue(chooser.choices(myFixture.editor, myFixture.file).any { it.name == "Align" })
    }

    fun `test chooser lists parent-specific wrappers first`() {
        settings.groupWrappers = true
        myFixture.configureByText("main.dart", dart("Stack(children: [Text('hi')]);"))
        caretAt("Text(")
        val names = WrapWithChooserIntention().choices(myFixture.editor, myFixture.file).map { it.name }
        assertEquals("Positioned", names.first())
    }

    fun `test caret on name only ignores carets inside the arguments`() {
        settings.caretOnNameOnly = true
        myFixture.configureByText("main.dart", dart("Text('hello');"))
        caretAt("hello")
        assertFalse("Caret in the string argument", available(WrapWithWidgetIntention("Align")))
        caretAt("Text")
        assertTrue("Caret on the constructor name", available(WrapWithWidgetIntention("Align")))
    }

    fun `test direct-parent wrappers have high priority`() {
        assertEquals(PriorityAction.Priority.HIGH, WrapWithWidgetIntention("Flexible").priority)
        assertEquals(PriorityAction.Priority.NORMAL, WrapWithWidgetIntention("Align").priority)
    }

    fun `test list slot detection`() {
        val stack = BuiltInWrappers.ALL.first { it.name == "Stack" }
        assertTrue(WrapperTemplateEngine.hasListSlot(stack))
        assertFalse(WrapperTemplateEngine.hasListSlot(BuiltInWrappers.ALL.first { it.name == "SafeArea" }))
        assertTrue(WrapperTemplateEngine.hasListSlot(WidgetWrapper("Wrap", listOf("Wrap(children: [\${widget}])"))))
    }

    fun `test surround with wraps a selected widget`() {
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        val text = myFixture.file.text
        val start = text.indexOf("Text")
        val end = start + "Text('hi')".length
        val descriptor = WidgetSurroundDescriptor()
        val elements = descriptor.getElementsToSurround(myFixture.file, start, end)
        assertEquals(1, elements.size)

        val safeArea = descriptor.surrounders.first { it.templateDescription == "SafeArea" }
        assertTrue(safeArea.isApplicable(elements))
        assertFalse("Flexible needs a flex parent", descriptor.surrounders.first { it.templateDescription == "Flexible" }.isApplicable(elements))
        WriteCommandAction.runWriteCommandAction(project) {
            safeArea.surroundElements(project, myFixture.editor, elements)
        }
        val result = myFixture.editor.document.text
        assertTrue("Got:\n$result", result.contains("SafeArea(") && result.contains("child: Text('hi')"))
    }

    fun `test surround with offers list wrappers for several siblings`() {
        myFixture.configureByText("main.dart", dart("Column(children: [Text('A'), Text('B')]);"))
        val text = myFixture.file.text
        val start = text.indexOf("Text('A')")
        val end = text.indexOf("Text('B')") + "Text('B')".length
        val descriptor = WidgetSurroundDescriptor()
        val elements = descriptor.getElementsToSurround(myFixture.file, start, end)
        assertEquals(2, elements.size)
        assertTrue(descriptor.surrounders.first { it.templateDescription == "Stack" }.isApplicable(elements))
        assertFalse(descriptor.surrounders.first { it.templateDescription == "SafeArea" }.isApplicable(elements))
    }

    fun `test selection intention offers a chooser when several list wrappers exist`() {
        myFixture.configureByText("main.dart", dart("Column(children: [Text('A'), Text('B')]);"))
        val text = myFixture.editor.document.text
        myFixture.editor.selectionModel.setSelection(
            text.indexOf("Text('A')"),
            text.indexOf("Text('B')") + "Text('B')".length,
        )
        val intention = WrapSelectionIntention()
        assertTrue(available(intention))
        assertEquals("Wrap 2 widgets with Stack", intention.text)

        settings.customWrappersJson = """[{"name":"Wrap","template":["Wrap(children: [${'$'}{widget}])"]}]"""
        assertTrue(available(intention))
        assertEquals("Wrap 2 widgets with…", intention.text)
    }
}
