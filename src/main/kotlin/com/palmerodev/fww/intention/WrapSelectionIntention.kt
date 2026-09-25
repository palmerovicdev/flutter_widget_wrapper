package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleListCellRenderer
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

    private var cachedCount: Int = 0
    private var cachedSingle: String? = null

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.wrapSelection")

    override fun getText(): String {
        val single = cachedSingle
        return when {
            cachedCount == 0 -> FlutterWidgetWrapperBundle.message("intention.text.wrapSelection.generic")
            single != null -> FlutterWidgetWrapperBundle.message("intention.text.wrapSelection", cachedCount, single)
            else -> FlutterWidgetWrapperBundle.message("intention.text.wrapSelection.chooser", cachedCount)
        }
    }

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    // The chosen wrapper decides between a live template and a direct edit.
    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        cachedCount = 0
        cachedSingle = null
        if (editor == null || file == null || !file.name.endsWith(".dart")) return false
        val selection = analyze(editor, file) ?: return false
        val wrappers = listWrappers()
        if (wrappers.isEmpty()) return false
        cachedCount = selection.elements.size
        cachedSingle = wrappers.singleOrNull()?.name
        return true
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val wrappers = listWrappers()
        when {
            wrappers.isEmpty() -> return
            wrappers.size == 1 -> wrap(project, editor, file, wrappers.single())
            else -> JBPopupFactory.getInstance()
                .createPopupChooserBuilder(wrappers)
                .setTitle(FlutterWidgetWrapperBundle.message("chooser.title.selection"))
                .setRenderer(SimpleListCellRenderer.create("") { it.name })
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

    private fun listWrappers(): List<WidgetWrapper> =
        WrapperRepository.all().filter { it.enabled && WrapperTemplateEngine.hasListSlot(it) }
}
