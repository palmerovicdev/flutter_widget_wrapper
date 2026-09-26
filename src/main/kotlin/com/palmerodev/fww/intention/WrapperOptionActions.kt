package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.settings.WrapperSettingsConfigurable
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.ProjectWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import com.palmerodev.fww.wrappers.WrapperRepository

/** Submenu entries of a "Wrap with X" intention. */
internal object WrapperOptionActions {

    /** Opens the wrapper in Settings, or the project file for a project wrapper. */
    class Edit(private val wrapperName: String) : BaseIntentionAction() {
        override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.option.family")
        override fun getText(): String = FlutterWidgetWrapperBundle.message("intention.option.edit", wrapperName)
        override fun startInWriteAction(): Boolean = false
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
            if (WrapperRepository.isProjectWrapper(wrapperName, project)) {
                ProjectWrappers.file(project)?.let { FileEditorManager.getInstance(project).openFile(it, true) }
                return
            }
            ShowSettingsUtil.getInstance().showSettingsDialog(project, WrapperSettingsConfigurable::class.java) {
                it.selectWrapper(wrapperName)
            }
        }

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
            IntentionPreviewInfo.EMPTY
    }

    /** Disables the wrapper so it no longer appears in the menu (re-enable in Settings). */
    class Hide(private val wrapperName: String) : BaseIntentionAction() {
        override fun getFamilyName(): String = FlutterWidgetWrapperBundle.message("intention.option.family")
        override fun getText(): String = FlutterWidgetWrapperBundle.message("intention.option.hide", wrapperName)
        override fun startInWriteAction(): Boolean = false

        // Project wrappers belong to the shared file; hiding them is done there.
        override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean =
            !WrapperRepository.isProjectWrapper(wrapperName, project)

        override fun invoke(project: Project, editor: Editor?, file: PsiFile?) = hide(wrapperName)

        override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
            IntentionPreviewInfo.EMPTY
    }

    fun hide(wrapperName: String) {
        val settings = FlutterWrapperSettings.getInstance()
        val custom = WrapperJsonCodec.parseList(settings.customWrappersJson)
        if (custom.any { it.name == wrapperName }) {
            settings.customWrappersJson = WrapperJsonCodec.encodeList(
                custom.map { if (it.name == wrapperName) it.copy(enabled = false) else it },
            )
        } else if (BuiltInWrappers.ALL.any { it.name == wrapperName }) {
            // A new set, so cached snapshots see the change.
            settings.disabledBuiltInNames = (settings.disabledBuiltInNames + wrapperName).toMutableSet()
        }
    }
}
