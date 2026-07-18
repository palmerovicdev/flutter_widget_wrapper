package com.palmerodev.fww

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.intention.WrapSelectionWithStackIntention
import com.palmerodev.fww.intention.WrapWithWidgetIntention

/**
 * End-to-end guard proving the dynamically registered wrap intentions actually reach the
 * Alt+Enter menu. Regression coverage for the pre-1.1.1 bug where several intentions sharing
 * one implementation class made IntentionManager.checkForDuplicates() throw and hid all of them.
 */
class IntentionAvailabilityIntegrationTest : BasePlatformTestCase() {

    fun `test wrap intentions register in the manager without duplicate collision`() {
        WrapIntentionRegistrar.syncRegistrations()
        // Touching getAvailableIntentions triggers checkForDuplicates(); it must not throw.
        val registered = IntentionManager.getInstance().intentionActions.map { it.text }
        assertTrue(
            "'Wrap with Align' should be registered",
            registered.any { it == "Wrap with Align" },
        )
    }

    fun `test isAvailable is true for a top level widget under the caret`() {
        myFixture.configureByText("main.dart", "Text('hi')")
        myFixture.editor.caretModel.moveToOffset(2)
        val available = WrapWithWidgetIntention("Align").isAvailable(project, myFixture.editor, myFixture.file)
        assertTrue("Wrap with Align should be available on a top-level Text", available)
    }

    fun `test wrap action surfaces in the Alt Enter list end to end`() {
        WrapIntentionRegistrar.syncRegistrations()
        myFixture.configureByText("main.dart", "Text('hi')")
        myFixture.editor.caretModel.moveToOffset(2)
        val available = myFixture.availableIntentions.map { it.text }
        assertTrue(
            "'Wrap with Align' should appear in the intention list. Got: $available",
            available.any { it == "Wrap with Align" },
        )
    }

    fun `test wrap selection with Stack replaces the selected siblings`() {
        myFixture.configureByText("main.dart", "Column(children: [Text('A'), Text('B')])")
        val text = myFixture.editor.document.text
        val selStart = text.indexOf("Text('A')")
        val selEnd = text.indexOf("Text('B')") + "Text('B')".length
        myFixture.editor.selectionModel.setSelection(selStart, selEnd)

        val intention = WrapSelectionWithStackIntention()
        assertTrue(
            "Stack intention should be available for two selected siblings",
            intention.isAvailable(project, myFixture.editor, myFixture.file),
        )
        WriteCommandAction.runWriteCommandAction(project) {
            intention.invoke(project, myFixture.editor, myFixture.file)
        }
        val result = myFixture.editor.document.text
        assertTrue("Result should contain a Stack wrapper. Got:\n$result", result.contains("Stack("))
        assertTrue("Stack should keep both children. Got:\n$result", result.contains("Text('A')") && result.contains("Text('B')"))
        assertTrue("Original flat children list should be gone. Got:\n$result", !result.contains("[Text('A'), Text('B')]"))
    }
}
