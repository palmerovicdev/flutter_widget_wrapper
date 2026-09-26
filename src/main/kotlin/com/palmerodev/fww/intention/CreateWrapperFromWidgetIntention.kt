package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.detection.WrappableFieldDetector
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.settings.WrapperFormDialog
import com.palmerodev.fww.settings.WrapperSettingsConfigurable
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec

class CreateWrapperFromWidgetIntention : BaseIntentionAction() {

    override fun getFamilyName(): String =
        FlutterWidgetWrapperBundle.message("intention.family.createWrapper")

    override fun getText(): String = cachedText.get() ?: getFamilyName()

    // Shared instance; see WrapSelectionIntention for why the text is thread-local.
    private val cachedText = ThreadLocal<String?>()

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (!file.name.endsWith(".dart")) return false
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file, offset) ?: return false
        val field = WrappableFieldDetector.find(detected.text) ?: return false
        cachedText.set(FlutterWidgetWrapperBundle.message("intention.text.createWrapper", detected.name))
        return field.fieldName in WrappableFieldDetector.WRAPPABLE_FIELDS
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val detected = FlutterWidgetDetector.detect(file, editor.caretModel.offset) ?: return
        val templateLines = templateFor(detected.text)?.split('\n') ?: return

        val settings = FlutterWrapperSettings.getInstance()
        val existingCustom = WrapperJsonCodec.parseList(settings.customWrappersJson)
        val customNames = existingCustom.mapTo(LinkedHashSet()) { it.name }
        val suggestionTaken = customNames + BuiltInWrappers.ALL.map { it.name }

        val initial = WidgetWrapper(
            name = suggestName(detected.name, suggestionTaken),
            template = templateLines,
            description = FlutterWidgetWrapperBundle.message("intention.createWrapper.description", detected.name),
            category = "Custom",
        )

        ApplicationManager.getApplication().invokeLater {
            val dialog = WrapperFormDialog(customNames, initial = initial)
            if (!dialog.showAndGet()) return@invokeLater
            val wrapper = dialog.result ?: return@invokeLater
            val updated = existingCustom.toMutableList().apply { add(wrapper) }
            settings.customWrappersJson = WrapperJsonCodec.encodeList(updated)
            WrapIntentionRegistrar.syncRegistrations()
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Flutter Widget Wrapper")
                .createNotification(
                    FlutterWidgetWrapperBundle.message("intention.createWrapper.done", wrapper.name),
                    NotificationType.INFORMATION,
                )
                .addAction(
                    NotificationAction.createSimpleExpiring(
                        FlutterWidgetWrapperBundle.message("notification.action.openSettings"),
                    ) {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, WrapperSettingsConfigurable::class.java)
                    },
                )
                .notify(project)
        }
    }

    /** Opens a dialog instead of editing code, so the preview shows the template it would save. */
    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo {
        val detected = FlutterWidgetDetector.detect(file, editor.caretModel.offset)
            ?: return IntentionPreviewInfo.EMPTY
        val template = templateFor(detected.text) ?: return IntentionPreviewInfo.EMPTY
        return IntentionPreviewInfo.Html(
            HtmlBuilder()
                .append(FlutterWidgetWrapperBundle.message("intention.createWrapper.preview"))
                .append(HtmlChunk.text(template).wrapWith("pre"))
                .toFragment(),
        )
    }

    /** The widget source with its child slot replaced by the `${widget}` placeholder. */
    private fun templateFor(widgetText: String): String? {
        val field = WrappableFieldDetector.find(widgetText) ?: return null
        val replacement = if (field.isList) $$"[${widget}]" else $$"${widget}"
        return normalizeIndent(
            widgetText.substring(0, field.valueStart) + replacement + widgetText.substring(field.valueEnd),
        )
    }

    private fun suggestName(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var idx = 2
        while ("${base}$idx" in taken) idx++
        return "${base}$idx"
    }

    private fun normalizeIndent(text: String): String {
        val lines = text.split('\n')
        if (lines.size <= 1) return text
        val nonBlank = lines.drop(1).filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return text
        val minIndent = nonBlank.minOf { line -> line.takeWhile { it == ' ' || it == '\t' }.length }
        if (minIndent == 0) return text
        return buildString {
            append(lines.first())
            for (i in 1 until lines.size) {
                append('\n')
                val line = lines[i]
                if (line.length >= minIndent) append(line.substring(minIndent)) else append(line)
            }
        }
    }
}
