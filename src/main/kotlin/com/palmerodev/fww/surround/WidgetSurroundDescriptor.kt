package com.palmerodev.fww.surround

import com.intellij.lang.surroundWith.SurroundDescriptor
import com.intellij.lang.surroundWith.Surrounder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartListLiteralExpression
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.detection.MultiWidgetSelectionDetector
import com.palmerodev.fww.intention.WrapApplier
import com.palmerodev.fww.intention.WrapTargets
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperRepository
import com.palmerodev.fww.wrappers.WrapperTemplateEngine

/**
 * Surround With (Ctrl+Alt+T / ⌥⌘T) for a selected widget, or several selected siblings of a
 * Row/Column/Flex `children:` list. Offers one entry per wrapper valid for the selection.
 */
class WidgetSurroundDescriptor : SurroundDescriptor {

    override fun getElementsToSurround(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement> {
        if (file !is DartFile) return PsiElement.EMPTY_ARRAY
        siblings(file, startOffset, endOffset)?.let { return it }
        return singleWidget(file, startOffset, endOffset)?.let { arrayOf(it) } ?: PsiElement.EMPTY_ARRAY
    }

    override fun getSurrounders(): Array<Surrounder> =
        WrapperRepository.all().filter { it.enabled }.map(::WrapperSurrounder).toTypedArray()

    override fun isExclusive(): Boolean = false

    /** The selection covers exactly one widget expression (surrounding whitespace ignored). */
    private fun singleWidget(file: PsiFile, startOffset: Int, endOffset: Int): PsiElement? {
        val text = file.text
        var start = startOffset
        var end = endOffset
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) return null
        val detected = FlutterWidgetDetector.detect(file, start) ?: return null
        if (detected.range != TextRange(start, end)) return null
        var element = file.findElementAt(start) ?: return null
        while (element.textRange != detected.range) element = element.parent ?: return null
        return element
    }

    private fun siblings(file: PsiFile, startOffset: Int, endOffset: Int): Array<PsiElement>? {
        val result = MultiWidgetSelectionDetector.analyze(file, startOffset, endOffset) ?: return null
        val leaf = file.findElementAt(result.start) ?: return null
        val list = PsiTreeUtil.getParentOfType(leaf, DartListLiteralExpression::class.java) ?: return null
        val selected = list.elementList.filter {
            it.textRange.startOffset >= result.start && it.textRange.endOffset <= result.end
        }
        return selected.takeIf { it.size >= 2 }?.toTypedArray()
    }

    private class WrapperSurrounder(private val wrapper: WidgetWrapper) : Surrounder {

        override fun getTemplateDescription(): String = wrapper.name

        override fun isApplicable(elements: Array<out PsiElement>): Boolean {
            if (elements.size > 1) return WrapperTemplateEngine.hasListSlot(wrapper)
            val element = elements.singleOrNull() ?: return false
            val detected = FlutterWidgetDetector.detect(element.containingFile, element.textRange.startOffset)
                ?: return false
            return WrapTargets.matches(wrapper, detected)
        }

        override fun surroundElements(project: Project, editor: Editor, elements: Array<out PsiElement>): TextRange? {
            val file = elements.first().containingFile
            val range = TextRange(elements.first().textRange.startOffset, elements.last().textRange.endOffset)
            val widgetText = WrapApplier.joinSiblings(elements.map { it.text })
            val plan = WrapApplier.plan(
                editor, file, wrapper, range, widgetText,
                constProbe = elements.first().textRange,
            )
            if (!WrapApplier.needsLiveTemplate(wrapper)) {
                // Surround With already runs in a write command.
                WrapApplier.replaceWithDefaults(project, editor, file, plan)
                return null
            }
            // A live template cannot start inside the surround write action; run it right after.
            ApplicationManager.getApplication().invokeLater({
                if (project.isDisposed || editor.isDisposed) return@invokeLater
                PsiDocumentManager.getInstance(project).commitDocument(editor.document)
                WrapApplier.apply(project, editor, file, plan)
            }, project.disposed)
            return null
        }
    }
}
