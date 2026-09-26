package com.palmerodev.fww.postfix

import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.intention.WrapApplier
import com.palmerodev.fww.intention.WrapTargets
import com.palmerodev.fww.model.DetectedWidget
import com.palmerodev.fww.wrappers.ProjectWrappers
import com.palmerodev.fww.wrappers.WrapperRepository

/**
 * Postfix templates for Dart: `Text('a').opacity` + Tab wraps `Text('a')` with Opacity.
 * One template per wrapper; the key is the lower-cased name (`.singlechildscrollview`).
 */
class WrapperPostfixTemplateProvider : PostfixTemplateProvider {

    override fun getId(): String = "fww.flutter.wrappers"

    override fun getPresentableName(): String = FlutterWidgetWrapperBundle.message("postfix.provider")

    // Built each time so new custom and project wrappers appear without a restart.
    override fun getTemplates(): Set<PostfixTemplate> =
        (WrapperRepository.all() + ProjectWrappers.fromOpenProjects())
            .distinctBy { it.name }
            .mapNotNull { wrapper -> keyOf(wrapper.name)?.let { WrapperPostfixTemplate(wrapper.name, it, this) } }
            .toSet()

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.'

    override fun preExpand(file: PsiFile, editor: Editor) = Unit

    override fun afterExpand(file: PsiFile, editor: Editor) = Unit

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile

    companion object {
        /** `.opacity` for "Opacity"; null when the name has no letters or digits. */
        fun keyOf(name: String): String? =
            name.lowercase().filter { it.isLetterOrDigit() }.takeIf { it.isNotEmpty() }?.let { ".$it" }

        /** The widget expression that ends exactly at [offset] (where the postfix key started). */
        fun widgetEndingAt(file: PsiFile, offset: Int): DetectedWidget? {
            if (offset <= 0) return null
            var element: PsiElement? = file.findElementAt(offset - 1)
            while (element != null && element.textRange.endOffset == offset) {
                val detected = FlutterWidgetDetector.detect(file, element.textRange.startOffset)
                if (detected != null && detected.range.endOffset == offset && detected.range.startOffset == element.textRange.startOffset) {
                    return detected
                }
                element = element.parent
            }
            return null
        }
    }
}

private class WrapperPostfixTemplate(
    private val wrapperName: String,
    key: String,
    provider: PostfixTemplateProvider,
) : PostfixTemplate(
    "fww.$wrapperName",
    key.removePrefix("."),
    key,
    "Text('a')$key",
    provider,
) {
    override fun getDescription(): String = FlutterWidgetWrapperBundle.message("postfix.description", wrapperName)

    override fun isApplicable(context: PsiElement, copyDocument: Document, newOffset: Int): Boolean {
        val file = context.containingFile ?: return false
        val wrapper = WrapperRepository.byName(wrapperName, file.project) ?: return false
        val widget = WrapperPostfixTemplateProvider.widgetEndingAt(file, newOffset) ?: return false
        return WrapTargets.matches(wrapper, widget)
    }

    // The wrap opens its own command (or live template).
    override fun startInWriteAction(): Boolean = false

    override fun expand(context: PsiElement, editor: Editor) {
        val file = context.containingFile ?: return
        val project = file.project
        PsiDocumentManager.getInstance(project).commitDocument(editor.document)
        val wrapper = WrapperRepository.byName(wrapperName, project) ?: return
        val widget = WrapperPostfixTemplateProvider.widgetEndingAt(file, editor.caretModel.offset) ?: return
        val plan = WrapApplier.plan(editor, file, wrapper, widget.range, widget.text)
        WrapApplier.applyInCommand(project, editor, file, plan)
    }
}
