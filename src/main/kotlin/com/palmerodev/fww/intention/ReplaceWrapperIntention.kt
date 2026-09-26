package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.FlutterWidgetWrapperIcons
import com.palmerodev.fww.detection.WrappableFieldDetector
import com.palmerodev.fww.model.DetectedWidget
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import javax.swing.Icon

/**
 * "Replace Flexible with…": swaps the widget under the caret for another wrapper, keeping
 * its `child:` (or the elements of its `children:`). Other arguments of the old widget are
 * dropped. Offered only with the caret on the constructor name, to keep the menu short.
 */
class ReplaceWrapperIntention : BaseIntentionAction(), Iconable {

    // Shared instance; see WrapSelectionIntention for why the text is thread-local.
    private val cachedText = ThreadLocal<String?>()

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.replace")

    override fun getText(): String = cachedText.get() ?: familyName

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    override fun startInWriteAction(): Boolean = false

    /** The widget to replace and the text that becomes the new wrapper's `${widget}`. */
    internal class Target(val widget: DetectedWidget, val childText: String, val isList: Boolean)

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        cachedText.remove()
        if (editor == null || file == null) return false
        val target = target(editor, file) ?: return false
        if (candidates(project, target).isEmpty()) return false
        cachedText.set(FlutterWidgetWrapperBundle.message("intention.text.replace", target.widget.name))
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val target = target(editor, file) ?: return
        val wrappers = candidates(project, target)
        if (wrappers.isEmpty()) return
        JBPopupFactory.getInstance()
            .createPopupChooserBuilder(wrappers)
            .setTitle(FlutterWidgetWrapperBundle.message("chooser.title.replace", target.widget.name))
            .setRenderer(WrapWithChooserIntention.WrapperRenderer())
            .setNamerForFiltering { it.name }
            .setItemChosenCallback { replace(project, editor, file, it) }
            .createPopup()
            .showInBestPositionFor(editor)
    }

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val target = target(editor, file) ?: return IntentionPreviewInfo.EMPTY
        val names = candidates(project, target).joinToString(", ") { it.name }
        return IntentionPreviewInfo.Html(
            HtmlBuilder().append(FlutterWidgetWrapperBundle.message("chooser.preview", names)).toFragment(),
        )
    }

    internal fun replace(project: Project, editor: Editor, file: PsiFile, wrapper: WidgetWrapper) {
        val target = target(editor, file) ?: return
        val plan = WrapApplier.plan(editor, file, wrapper, target.widget.range, target.childText)
        WrapApplier.applyInCommand(project, editor, file, plan)
    }

    internal fun target(editor: Editor, file: PsiFile): Target? {
        if (!file.name.endsWith(".dart")) return null
        val widget = WrapTargets.widgetAt(editor, file) ?: return null
        if (!WrapTargets.isOnConstructorName(widget, editor.caretModel.offset)) return null
        val field = WrappableFieldDetector.find(widget.text) ?: return null
        if (field.fieldName != "child" && field.fieldName != "children") return null
        val value = widget.text.substring(field.valueStart, field.valueEnd)
        val childText = if (field.isList) listElements(value) ?: return null else value
        return Target(widget, childText, field.isList)
    }

    /**
     * Wrappers valid where the widget sits, except the widget itself. A `children:` list
     * only moves to wrappers that also take a list.
     */
    internal fun candidates(project: Project, target: Target): List<WidgetWrapper> =
        WrapTargets.wrappersFor(target.widget, project)
            .filter { it.name != target.widget.name }
            .filter { !target.isList || WrapperTemplateEngine.hasListSlot(it) }
            .sortedByDescending { it.requiresDirectParent }

    /** `[a, b,]` → `a, b`; null when the value is not a list literal. */
    private fun listElements(value: String): String? {
        val trimmed = value.trim().removePrefix("const").trim()
        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) return null
        return trimmed.substring(1, trimmed.length - 1).trim().removeSuffix(",").trim().ifEmpty { null }
    }
}
