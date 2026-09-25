package com.palmerodev.fww

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.intention.WrapWithWidgetIntention
import com.palmerodev.fww.settings.FlutterWrapperSettings

/**
 * 1.3 coverage: intention preview for tab-stop wrappers, wraps inside `const` expressions,
 * detection cache invalidation, and unregistering deleted custom wrappers.
 */
class WrapPreviewAndConstTest : BasePlatformTestCase() {

    private fun dart(body: String): String = "void f() {\n$body\n}"

    override fun tearDown() {
        try {
            FlutterWrapperSettings.getInstance().apply {
                customWrappersJson = ""
                disabledBuiltInNames = mutableSetOf()
            }
            WrapIntentionRegistrar.syncRegistrations()
        } finally {
            super.tearDown()
        }
    }

    private fun caretOn(marker: String) {
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(marker) + 1)
    }

    fun `test tab-stop wrapper previews with default values`() {
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        caretOn("Text")
        val preview = myFixture.getIntentionPreviewText(WrapWithWidgetIntention("Opacity"))
        assertNotNull("Opacity should have a preview", preview)
        assertTrue("Preview should wrap the widget. Got:\n$preview", preview!!.contains("Opacity("))
        assertTrue("Preview should use the default value. Got:\n$preview", preview.contains("opacity: 0.5"))
        assertFalse("Preview must not show raw markers. Got:\n$preview", preview.contains("\${"))
        assertEquals("Preview must not touch the real file", dart("Text('hi');"), myFixture.editor.document.text)
    }

    fun `test preview keeps dart interpolation of the wrapped widget`() {
        myFixture.configureByText("main.dart", dart("Text('\${name}');"))
        caretOn("Text")
        val preview = myFixture.getIntentionPreviewText(WrapWithWidgetIntention("Opacity"))
        assertTrue("Interpolation must survive. Got:\n$preview", preview!!.contains("Text('\${name}')"))
    }

    fun `test wrapping with a closure removes the enclosing const`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("main.dart", dart("const Column(children: [Text('a')]);"))
        caretOn("Text")
        WrapWithWidgetIntention("GestureDetector").invoke(project, myFixture.editor, myFixture.file)

        val result = myFixture.editor.document.text
        assertTrue("Expected GestureDetector. Got:\n$result", result.contains("GestureDetector("))
        assertFalse("The enclosing const must be removed. Got:\n$result", result.contains("const Column"))
        assertTrue("The Column must remain. Got:\n$result", result.contains("Column("))
    }

    fun `test wrapping with a const-compatible wrapper keeps the enclosing const`() {
        myFixture.configureByText("main.dart", dart("const Column(children: [Text('a')]);"))
        caretOn("Text")
        WriteCommandAction.runWriteCommandAction(project) {
            WrapWithWidgetIntention("SafeArea").invoke(project, myFixture.editor, myFixture.file)
        }
        val result = myFixture.editor.document.text
        assertTrue("Expected SafeArea. Got:\n$result", result.contains("SafeArea("))
        assertTrue("SafeArea is const-compatible; keep the const. Got:\n$result", result.contains("const Column"))
    }

    fun `test detection cache follows document edits`() {
        myFixture.configureByText("main.dart", dart("Text('a');"))
        val offset = myFixture.file.text.indexOf("Text") + 1
        assertEquals("Text", FlutterWidgetDetector.detect(myFixture.file, offset)?.name)

        WriteCommandAction.runWriteCommandAction(project) {
            val doc = myFixture.editor.document
            doc.replaceString(offset - 1, offset - 1 + "Text('a')".length, "Icon(Icons.add)")
            PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
        assertEquals("Icon", FlutterWidgetDetector.detect(myFixture.file, offset)?.name)
    }

    fun `test deleted custom wrapper is unregistered`() {
        val settings = FlutterWrapperSettings.getInstance()
        settings.customWrappersJson = """[{"name":"MyCard","template":["Card(","  child: ${'$'}{widget},",")"]}]"""
        WrapIntentionRegistrar.syncRegistrations()
        assertTrue(IntentionManager.getInstance().intentionActions.any { it.text == "Wrap with MyCard" })

        settings.customWrappersJson = ""
        WrapIntentionRegistrar.syncRegistrations()
        assertFalse(
            "MyCard should be unregistered after deletion",
            IntentionManager.getInstance().intentionActions.any { it.text == "Wrap with MyCard" },
        )
        assertTrue(
            "Built-ins stay registered",
            IntentionManager.getInstance().intentionActions.any { it.text == "Wrap with Align" },
        )
    }
}
