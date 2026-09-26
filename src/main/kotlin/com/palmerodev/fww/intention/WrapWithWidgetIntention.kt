package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionWithOptions
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.FlutterWidgetWrapperIcons
import com.palmerodev.fww.wrappers.WrapperRepository
import javax.swing.Icon

class WrapWithWidgetIntention(private val wrapperName: String) :
    BaseIntentionAction(), PriorityAction, Iconable, IntentionActionWithOptions {

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.wrap")

    override fun getText(): String = FlutterWidgetWrapperBundle.message("intention.text.wrap", wrapperName)

    // Always false: the wrap opens its own command, because whether it needs a write action
    // (direct edit) or must run outside one (live template) depends on the wrapper that is
    // resolved for the current project.
    override fun startInWriteAction(): Boolean = false

    /**
     * Wrappers restricted to a direct parent (Flexible under a Row, Positioned under a Stack)
     * only show up where they are the specific fit, so they go to the top of the menu.
     */
    override fun getPriority(): PriorityAction.Priority {
        val wrapper = WrapperRepository.byName(wrapperName) ?: return PriorityAction.Priority.NORMAL
        return if (wrapper.requiresDirectParent) PriorityAction.Priority.HIGH else PriorityAction.Priority.NORMAL
    }

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    /** The Alt+Enter submenu (right arrow): edit or hide this wrapper. */
    override fun getOptions(): List<IntentionAction> =
        listOf(WrapperOptionActions.Edit(wrapperName), WrapperOptionActions.Hide(wrapperName))

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (WrapTargets.grouped()) return false
        val wrapper = WrapperRepository.byName(wrapperName, project) ?: return false
        val detected = WrapTargets.widgetAt(editor, file) ?: return false
        return WrapTargets.matches(wrapper, detected)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val plan = plan(project, editor, file) ?: return
        WrapApplier.applyInCommand(project, editor, file, plan)
    }

    /**
     * The platform cannot build a preview for an intention that does not start in a write
     * action, so render the wrapper with its tab-stop defaults on the preview copy.
     */
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val plan = plan(project, editor, file) ?: return IntentionPreviewInfo.EMPTY
        WrapApplier.replaceWithDefaults(project, editor, file, plan)
        return IntentionPreviewInfo.DIFF
    }

    private fun plan(project: Project, editor: Editor, file: PsiFile): WrapApplier.Plan? {
        val wrapper = WrapperRepository.byName(wrapperName, project) ?: return null
        val detected = WrapTargets.widgetAt(editor, file) ?: return null
        return WrapApplier.plan(editor, file, wrapper, detected.range, detected.text)
    }
}
