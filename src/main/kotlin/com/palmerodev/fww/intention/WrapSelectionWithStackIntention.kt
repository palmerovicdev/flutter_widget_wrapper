package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.palmerodev.fww.detection.MultiWidgetSelectionDetector

/**
 * Available when several sibling widgets are selected inside a Row/Column/Flex `children:` list;
 * replaces the whole selection with a single `Stack` holding them as its children.
 */
class WrapSelectionWithStackIntention : BaseIntentionAction() {

    private var cachedCount: Int = 0

    override fun getFamilyName(): String = "Wrap Flutter widgets"

    override fun getText(): String =
        if (cachedCount > 0) "Wrap $cachedCount widgets with Stack" else "Wrap selected widgets with Stack"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (!file.name.endsWith(".dart")) return false
        val selection = editor.selectionModel
        if (!selection.hasSelection()) return false
        val result = MultiWidgetSelectionDetector.analyze(
            editor.document.text,
            selection.selectionStart,
            selection.selectionEnd,
        ) ?: return false
        cachedCount = result.elements.size
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val document = editor.document
        val selection = editor.selectionModel
        val result = MultiWidgetSelectionDetector.analyze(
            document.text,
            selection.selectionStart,
            selection.selectionEnd,
        ) ?: return

        val lineStart = document.getLineStartOffset(document.getLineNumber(result.start))
        val baseIndent = document
            .getText(TextRange(lineStart, result.start))
            .takeWhile { it == ' ' || it == '\t' }
        val childIndent = "$baseIndent    "

        val replacement = buildString {
            append("Stack(\n")
            append(baseIndent).append("  children: [\n")
            for (element in result.elements) {
                append(reindent(element, childIndent)).append(",\n")
            }
            append(baseIndent).append("  ],\n")
            append(baseIndent).append(")")
        }

        document.replaceString(result.start, result.end, replacement)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        CodeStyleManager.getInstance(project)
            .reformatRange(file, result.start, result.start + replacement.length)
    }

    /** Re-bases a possibly multi-line element onto [childIndent], preserving inner structure. */
    private fun reindent(element: String, childIndent: String): String {
        val lines = element.split('\n')
        if (lines.size == 1) return childIndent + element
        val rest = lines.drop(1)
        val minIndent = rest.filter { it.isNotBlank() }
            .minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
            ?: 0
        return buildString {
            append(childIndent).append(lines.first())
            for (line in rest) {
                append('\n').append(childIndent)
                append(if (line.length >= minIndent) line.substring(minIndent) else line)
            }
        }
    }
}
