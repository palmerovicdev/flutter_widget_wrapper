package com.palmerodev.fww

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.intention.WrapWithWidgetIntention

/**
 * End-to-end coverage for the live-template wrap path (Idea 2). A wrapper whose template
 * carries tab-stop markers launches an interactive template pre-filled with the marker
 * defaults; a markerless wrapper keeps editing the document directly.
 */
class WrapWithLiveTemplateTest : BasePlatformTestCase() {

    /** Wrap in a function body so the Dart PSI builds real call expressions. */
    private fun dart(body: String): String = "void f() {\n$body\n}"

    fun `test wrapping with a marker wrapper starts a template filled with defaults`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        WrapIntentionRegistrar.syncRegistrations()
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("Text") + 2)

        val intention = WrapWithWidgetIntention("Opacity")
        assertTrue("Opacity should be available on a top-level Text", intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertFalse("Marker wrappers must not start in a write action", intention.startInWriteAction())
        intention.invoke(project, myFixture.editor, myFixture.file)

        val result = myFixture.editor.document.text
        assertTrue("Expected Opacity wrapper. Got:\n$result", result.contains("Opacity("))
        assertTrue("Expected the default opacity value. Got:\n$result", result.contains("opacity: 0.5"))
        assertTrue("Expected the wrapped widget preserved. Got:\n$result", result.contains("Text('hi')"))
        assertFalse("Raw markers should be gone. Got:\n$result", result.contains("\${"))
    }

    fun `test markerless wrapper still edits the document directly`() {
        WrapIntentionRegistrar.syncRegistrations()
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("Text") + 2)

        val intention = WrapWithWidgetIntention("SafeArea")
        assertTrue("SafeArea should be available", intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertTrue("Markerless wrappers keep the direct-edit write action", intention.startInWriteAction())
        WriteCommandAction.runWriteCommandAction(project) {
            intention.invoke(project, myFixture.editor, myFixture.file)
        }
        val result = myFixture.editor.document.text
        assertTrue("Expected SafeArea wrapper. Got:\n$result", result.contains("SafeArea("))
        assertTrue("Expected the child preserved. Got:\n$result", result.contains("child: Text('hi')"))
    }
}
