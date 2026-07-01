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
import com.palmerodev.fww.model.FlutterWidgetContext
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperTemplateEngine

class WrapWithWidgetIntention(private val wrapper: WidgetWrapper) : BaseIntentionAction() {

    override fun getFamilyName(): String = "Wrap Flutter widget"

    override fun getText(): String = "Wrap with ${wrapper.name}"

    override fun startInWriteAction(): Boolean = true

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (!wrapper.enabled) return false
        if (editor == null || file == null) return false
        if (!file.name.endsWith(".dart")) return false
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file.name, editor.document.text, offset) ?: return false
        val context = FlutterContextAnalyzer.analyze(detected)
        return matchesContext(context)
    }

    private fun matchesContext(ctx: FlutterWidgetContext): Boolean {
        if (ctx.parentWidgetName != null && ctx.parentWidgetName in wrapper.disallowedParents) return false
        if (wrapper.requiresDirectParent) {
            val direct = ctx.parentWidgetName ?: return false
            return direct in wrapper.allowedParents
        }
        if ("any" in wrapper.allowedParents) return true
        val chain = buildList {
            ctx.parentWidgetName?.let { add(it) }
            addAll(ctx.ancestors)
        }
        return chain.any { it in wrapper.allowedParents }
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val document = editor.document
        val text = document.text
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file.name, text, offset) ?: return

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
