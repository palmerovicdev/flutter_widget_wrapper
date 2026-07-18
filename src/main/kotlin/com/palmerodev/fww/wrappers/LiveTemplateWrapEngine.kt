package com.palmerodev.fww.wrappers

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.palmerodev.fww.model.WidgetWrapper

/**
 * Wraps a widget using an interactive IntelliJ live template, so the caret jumps to the
 * editable values (tab-stops) declared with `${name:default}` markers and finishes at `${end}`.
 *
 * Only used when [TabStops.hasTabStops] is true; wrappers without markers keep the plain
 * text-replacement path in `WrapWithWidgetIntention`.
 */
object LiveTemplateWrapEngine {

    /** Stand-in for a literal `${` inside the user's widget source, so Dart string
     *  interpolation is never mistaken for a tab-stop while tokenizing the rendered text. */
    private const val DOLLAR_BRACE = "\u0001"

    /**
     * Replaces `[startOffset, endOffset)` in [editor] with the rendered wrapper and starts the
     * template. Must be called outside an enclosing write action — [TemplateManager.startTemplate]
     * runs its own command (hence `WrapWithWidgetIntention.startInWriteAction()` returns false
     * for marker-bearing wrappers).
     */
    fun startWrap(
        project: Project,
        editor: Editor,
        wrapper: WidgetWrapper,
        widgetText: String,
        baseIndent: String,
        startOffset: Int,
        endOffset: Int,
    ) {
        val template = buildTemplate(project, wrapper, widgetText, baseIndent)
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(startOffset, endOffset, "")
            PsiDocumentManager.getInstance(project).commitDocument(document)
            editor.caretModel.moveToOffset(startOffset)
        }
        TemplateManager.getInstance(project).startTemplate(editor, template)
    }

    private fun buildTemplate(
        project: Project,
        wrapper: WidgetWrapper,
        widgetText: String,
        baseIndent: String,
    ): Template {
        val template = TemplateManager.getInstance(project).createTemplate("", "")
        template.isToReformat = true

        // Render the wrapper with the widget in place (multi-line indentation handled by the
        // existing engine) while protecting the widget's own `${` from being read as markers.
        val safeWidget = widgetText.replace("\${", DOLLAR_BRACE)
        val rendered = WrapperTemplateEngine.apply(wrapper, safeWidget, baseIndent)

        val declared = mutableSetOf<String>()
        for (token in TabStops.tokenize(rendered)) {
            when (token) {
                is TabStops.Token.Literal ->
                    template.addTextSegment(token.text.replace(DOLLAR_BRACE, "\${"))

                is TabStops.Token.Variable -> {
                    val id = sanitize(token.name)
                    if (declared.add(id)) {
                        template.addVariable(id, ConstantNode(token.default), ConstantNode(token.default), true)
                    } else {
                        template.addVariableSegment(id)
                    }
                }

                TabStops.Token.End -> template.addEndVariable()

                // Unreachable after apply() has already substituted ${widget}; kept for safety.
                TabStops.Token.Widget -> template.addTextSegment("\${widget}")
            }
        }
        return template
    }

    /** Turns a marker name into a valid, stable template-variable identifier. */
    private fun sanitize(name: String): String {
        val cleaned = buildString {
            for (c in name) append(if (c.isLetterOrDigit() || c == '_') c else '_')
        }
        return when {
            cleaned.isEmpty() -> "v"
            cleaned.first().isDigit() -> "v$cleaned"
            else -> cleaned
        }
    }
}
