package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.FlutterWidgetWrapperIcons
import com.palmerodev.fww.detection.MultiWidgetSelectionDetector
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperRepository
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import javax.swing.Icon

/**
 * Available when several sibling widgets are selected inside a Row/Column/Flex `children:`
 * list; wraps the whole selection with a wrapper whose `${widget}` sits in a list
 * (built-in `Stack`, or custom ones such as `Wrap(children: [${widget}])`).
 */
class WrapSelectionIntention : BaseIntentionAction(), Iconable {

    // One registered instance serves every editor; the platform asks for the text right
    // after isAvailable on the same thread, so a thread-local keeps concurrent editors apart.
    // It holds the resolved text: a String, unlike a plugin class, doesn't pin the plugin's
    // class loader to pool threads after a dynamic unload.
    private val cachedText = ThreadLocal<String?>()

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.wrapSelection")

    override fun getText(): String =
        cachedText.get() ?: FlutterWidgetWrapperBundle.message("intention.text.wrapSelection.generic")

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    // The chosen wrapper decides between a live template and a direct edit.
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        cachedText.remove()
        if (editor == null || file == null || !file.name.endsWith(".dart")) return false
        val selection = analyze(editor, file) ?: return false
        val wrappers = listWrappers(project)
        if (wrappers.isEmpty()) return false
        val count = selection.elements.size
        val single = wrappers.singleOrNull()?.name
        cachedText.set(
            if (single != null) {
                FlutterWidgetWrapperBundle.message("intention.text.wrapSelection", count, single)
            } else {
                FlutterWidgetWrapperBundle.message("intention.text.wrapSelection.chooser", count)
            },
        )
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val wrappers = listWrappers(project)
        when {
            wrappers.isEmpty() -> return
            wrappers.size == 1 -> wrap(project, editor, file, wrappers.single())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(wrappers)
                .setTitle(FlutterWidgetWrapperBundle.message("chooser.title.selection"))
                .setRenderer(WrapWithChooserIntention.WrapperRenderer())
                .setNamerForFiltering { it.name }
                .setItemChosenCallback { wrap(project, editor, file, it) }
                .createPopup()
                .showInBestPositionFor(editor)
        }
    }

    private fun wrap(project: Project, editor: Editor, file: PsiFile, wrapper: WidgetWrapper) {
        val selection = analyze(editor, file) ?: return
        val first = selection.elements.first()
        val plan = WrapApplier.plan(
            editor, file, wrapper,
            range = TextRange(selection.start, selection.end),
            widgetText = WrapApplier.joinSiblings(selection.elements),
            constProbe = TextRange(selection.start, selection.start + first.length),
        )
        WrapApplier.applyInCommand(project, editor, file, plan)
    }

    private fun analyze(editor: Editor, file: PsiFile): MultiWidgetSelectionDetector.Result? {
        val selection = editor.selectionModel
        if (!selection.hasSelection()) return null
        return MultiWidgetSelectionDetector.analyze(file, selection.selectionStart, selection.selectionEnd)
    }

    private fun listWrappers(project: Project): List<WidgetWrapper> =
        WrapperRepository.all(project).filter { it.enabled && WrapperTemplateEngine.hasListSlot(it) }
}
