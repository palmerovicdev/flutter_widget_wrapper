package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiFile
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.FlutterWidgetWrapperIcons
import com.palmerodev.fww.model.WidgetWrapper
import javax.swing.Icon
import javax.swing.JList

/**
 * The single "Wrap with…" entry used when the user groups wrappers (Settings). Opens a
 * searchable popup with the wrappers valid for the widget under the caret.
 */
class WrapWithChooserIntention : BaseIntentionAction(), Iconable {

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.wrap")

    override fun getText(): String = FlutterWidgetWrapperBundle.message("intention.text.chooser")

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (!WrapTargets.grouped()) return false
        return choices(editor, file).isNotEmpty()
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val detected = WrapTargets.widgetAt(editor, file) ?: return
        val wrappers = choices(editor, file)
        if (wrappers.isEmpty()) return

        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(wrappers)
            .setTitle(FlutterWidgetWrapperBundle.message("chooser.title", detected.name))
            .setRenderer(WrapperRenderer())
            .setNamerForFiltering { it.name }
            .setItemChosenCallback { wrapper ->
                // Re-detect: the document is committed, but the caret may have moved.
                val target = WrapTargets.widgetAt(editor, file) ?: return@setItemChosenCallback
                val plan = WrapApplier.plan(editor, file, wrapper, target.range, target.text)
                WrapApplier.applyInCommand(project, editor, file, plan)
            }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val names = choices(editor, file).joinToString(", ") { it.name }
        if (names.isEmpty()) return IntentionPreviewInfo.EMPTY
        return IntentionPreviewInfo.Html(
            HtmlBuilder().append(FlutterWidgetWrapperBundle.message("chooser.preview", names)).toFragment(),
        )
    }

    /** Valid wrappers at the caret; parent-specific ones (Flexible, Positioned) first. */
    internal fun choices(editor: Editor, file: PsiFile): List<WidgetWrapper> {
        val detected = WrapTargets.widgetAt(editor, file) ?: return emptyList()
        return WrapTargets.wrappersFor(detected).sortedByDescending { it.requiresDirectParent }
    }

    private class WrapperRenderer : ColoredListCellRenderer<WidgetWrapper>() {
        override fun customizeCellRenderer(
            list: JList<out WidgetWrapper>,
            value: WidgetWrapper?,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean,
        ) {
            value ?: return
            icon = FlutterWidgetWrapperIcons.Wrap
            append(value.name)
            append("  ${value.category}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}
