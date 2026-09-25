package com.palmerodev.fww.intention

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.detection.ConstContext
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.LiveTemplateWrapEngine
import com.palmerodev.fww.wrappers.TabStops
import com.palmerodev.fww.wrappers.WrapperTemplateEngine

/**
 * Applies a wrapper to a source range. Shared by the per-wrapper intentions, the
 * "Wrap with…" chooser, Surround With and multi-widget selection.
 */
internal object WrapApplier {

    class Plan(
        val wrapper: WidgetWrapper,
        /** The source range being wrapped (one widget, or several siblings). */
        val range: TextRange,
        /** The text substituted for `${widget}`. */
        val widgetText: String,
        val baseIndent: String,
        /** The wrapper applied to [widgetText] with every tab-stop at its default value. */
        val rendered: String,
        val constKeywords: List<TextRange>,
    )

    /**
     * [constProbe] is the range of one wrapped widget expression; its enclosing `const`
     * keywords are removed when the wrapper cannot live in a const context.
     */
    fun plan(
        editor: Editor,
        file: PsiFile,
        wrapper: WidgetWrapper,
        range: TextRange,
        widgetText: String,
        constProbe: TextRange = range,
    ): Plan {
        val document = editor.document
        val lineStart = document.getLineStartOffset(document.getLineNumber(range.startOffset))
        val baseIndent = document
            .getText(TextRange(lineStart, range.startOffset))
            .takeWhile { it == ' ' || it == '\t' }
        // Strip the markers from the template, not from the result: the widget's own Dart
        // interpolation (`'${name}'`) must not be read as a tab-stop.
        val defaults = wrapper.copy(template = wrapper.template.map(TabStops::stripToDefaults))
        val rendered = WrapperTemplateEngine.apply(defaults, widgetText, baseIndent)
        val constKeywords =
            if (ConstContext.requiresNonConstContext(defaults.template.joinToString("\n"))) {
                ConstContext.enclosingConstKeywords(file, constProbe)
            } else {
                emptyList()
            }
        return Plan(wrapper, range, widgetText, baseIndent, rendered, constKeywords)
    }

    /** True when [apply] must run outside a write action (it starts a live template). */
    fun needsLiveTemplate(wrapper: WidgetWrapper): Boolean = TabStops.hasTabStops(wrapper)

    /**
     * Performs the wrap. Tab-stop wrappers start a live template (own command); the others
     * edit the document directly, which requires the caller to hold a write action.
     */
    fun apply(project: Project, editor: Editor, file: PsiFile, plan: Plan) {
        if (needsLiveTemplate(plan.wrapper)) {
            LiveTemplateWrapEngine.startWrap(
                project, editor, plan.wrapper, plan.widgetText, plan.baseIndent,
                plan.range.startOffset, plan.range.endOffset, plan.constKeywords,
            )
        } else {
            replaceWithDefaults(project, editor, file, plan)
        }
        showWarning(editor, plan.wrapper)
    }

    /** [apply] for callers outside a write action (popups, choosers). */
    fun applyInCommand(project: Project, editor: Editor, file: PsiFile, plan: Plan) {
        if (needsLiveTemplate(plan.wrapper)) {
            apply(project, editor, file, plan)
            return
        }
        val name = FlutterWidgetWrapperBundle.message("intention.text.wrap", plan.wrapper.name)
        WriteCommandAction.runWriteCommandAction(project, name, null, { replaceWithDefaults(project, editor, file, plan) }, file)
        showWarning(editor, plan.wrapper)
    }

    /** Replaces the range with [Plan.rendered] and reformats; also used for previews. */
    fun replaceWithDefaults(project: Project, editor: Editor, file: PsiFile, plan: Plan) {
        val document = editor.document
        val replacement = plan.rendered
        document.replaceString(plan.range.startOffset, plan.range.endOffset, replacement)
        // The const keywords all precede the range, so they shift the new start offset.
        val start = plan.range.startOffset - ConstContext.removeKeywords(document, plan.constKeywords)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        CodeStyleManager.getInstance(project).reformatRange(file, start, start + replacement.length)
    }

    private fun showWarning(editor: Editor, wrapper: WidgetWrapper) {
        val warning = wrapper.warning?.takeIf { it.isNotBlank() } ?: return
        if (ApplicationManager.getApplication().isHeadlessEnvironment) return
        HintManager.getInstance().showInformationHint(editor, warning)
    }

    /**
     * Joins the siblings as one `${widget}` value: each element keeps its inner structure,
     * re-based to column 0 so the template engine can indent it.
     */
    fun joinSiblings(elements: List<String>): String =
        elements.joinToString(",\n") { element ->
            val lines = element.split('\n')
            if (lines.size == 1) return@joinToString element
            val rest = lines.drop(1)
            val minIndent = rest.filter { it.isNotBlank() }
                .minOfOrNull { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
                ?: 0
            (listOf(lines.first()) + rest.map { if (it.length >= minIndent) it.substring(minIndent) else it })
                .joinToString("\n")
        }
}
