package com.palmerodev.fww.intention

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
    BaseIntentionAction(), PriorityAction, Iconable {

    override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.family.wrap")

    override fun getText(): String = FlutterWidgetWrapperBundle.message("intention.text.wrap", wrapperName)

    // Marker-bearing wrappers launch a live template, which manages its own write command;
    // markerless wrappers keep editing the document directly inside a write action.
    override fun startInWriteAction(): Boolean {
        val wrapper = WrapperRepository.byName(wrapperName) ?: return true
        return !WrapApplier.needsLiveTemplate(wrapper)
    }

    /**
     * Wrappers restricted to a direct parent (Flexible under a Row, Positioned under a Stack)
     * only show up where they are the specific fit, so they go to the top of the menu.
     */
    override fun getPriority(): PriorityAction.Priority {
        val wrapper = WrapperRepository.byName(wrapperName) ?: return PriorityAction.Priority.NORMAL
        return if (wrapper.requiresDirectParent) PriorityAction.Priority.HIGH else PriorityAction.Priority.NORMAL
    }

    override fun getIcon(flags: Int): Icon = FlutterWidgetWrapperIcons.Wrap

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (WrapTargets.grouped()) return false
        val wrapper = WrapperRepository.byName(wrapperName) ?: return false
        val detected = WrapTargets.widgetAt(editor, file) ?: return false
        return WrapTargets.matches(wrapper, detected)
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val plan = plan(editor, file) ?: return
        WrapApplier.apply(project, editor, file, plan)
    }

    /**
     * Live-template wrappers do not start in a write action, so the platform cannot build
     * a preview by running [invoke] on a copy. Render the tab-stop defaults instead, which
     * gives every wrapper the same diff preview.
     */
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val plan = plan(editor, file) ?: return IntentionPreviewInfo.EMPTY
        WrapApplier.replaceWithDefaults(project, editor, file, plan)
        return IntentionPreviewInfo.DIFF
    }

    private fun plan(editor: Editor, file: PsiFile): WrapApplier.Plan? {
        val wrapper = WrapperRepository.byName(wrapperName) ?: return null
        val detected = WrapTargets.widgetAt(editor, file) ?: return null
        return WrapApplier.plan(editor, file, wrapper, detected.range, detected.text)
    }
}
