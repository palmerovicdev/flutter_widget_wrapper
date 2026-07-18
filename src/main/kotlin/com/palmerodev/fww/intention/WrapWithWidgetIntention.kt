package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.palmerodev.fww.detection.FlutterContextAnalyzer
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.wrappers.WrapperContextMatcher
import com.palmerodev.fww.wrappers.WrapperRepository
import com.palmerodev.fww.wrappers.WrapperTemplateEngine

class WrapWithWidgetIntention(private val wrapperName: String) : BaseIntentionAction() {

    override fun getFamilyName(): String = "Wrap Flutter widget"

    override fun getText(): String = "Wrap with $wrapperName"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (!file.name.endsWith(".dart")) return false
        val wrapper = WrapperRepository.byName(wrapperName) ?: return false
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file, offset) ?: return false
        val context = FlutterContextAnalyzer.analyze(detected)
        return WrapperContextMatcher.matches(wrapper, context)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val wrapper = WrapperRepository.byName(wrapperName) ?: return
        val document = editor.document
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file, offset) ?: return

        val startOffset = detected.range.startOffset
        val endOffset = detected.range.endOffset
        val lineStart = document.getLineStartOffset(document.getLineNumber(startOffset))
        val baseIndent = document
            .getText(TextRange(lineStart, startOffset))
            .takeWhile { it == ' ' || it == '\t' }

        val replacement = WrapperTemplateEngine.apply(wrapper, detected.text, baseIndent)
        document.replaceString(startOffset, endOffset, replacement)
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val newEnd = startOffset + replacement.length
        CodeStyleManager.getInstance(project).reformatRange(file, startOffset, newEnd)
    }
}
