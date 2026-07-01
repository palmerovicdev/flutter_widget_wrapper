package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.impl.BaseIntentionAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiFile
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.detection.WrappableFieldDetector
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.settings.WrapperFormDialog
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec

class CreateWrapperFromWidgetIntention : BaseIntentionAction() {

    override fun getFamilyName(): String =
        FlutterWidgetWrapperBundle.message("intention.family.createWrapper")

    override fun getText(): String = cachedText ?: getFamilyName()

    private var cachedText: String? = null

    override fun startInWriteAction(): Boolean = false

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
        if (editor == null || file == null) return false
        if (!file.name.endsWith(".dart")) return false
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file.name, editor.document.text, offset) ?: return false
        val field = WrappableFieldDetector.find(detected.text) ?: return false
        cachedText = FlutterWidgetWrapperBundle.message("intention.text.createWrapper", detected.name)
        return field.fieldName in WrappableFieldDetector.WRAPPABLE_FIELDS
    }

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        if (editor == null || file == null) return
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file.name, editor.document.text, offset) ?: return
        val field = WrappableFieldDetector.find(detected.text) ?: return

        val replacement = if (field.isList) "[\${widget}]" else "\${widget}"
        val templated = detected.text.substring(0, field.valueStart) +
            replacement +
            detected.text.substring(field.valueEnd)

        val templateLines = normalizeIndent(templated).split('\n')

        val settings = FlutterWrapperSettings.getInstance()
        val existingCustom = WrapperJsonCodec.parseList(settings.customWrappersJson)
        val existingNames = LinkedHashSet<String>().apply {
            addAll(BuiltInWrappers.ALL.map { it.name })
            addAll(existingCustom.map { it.name })
        }

        val initial = WidgetWrapper(
            name = suggestName(detected.name, existingNames),
            template = templateLines,
            description = "Wraps with ${detected.name}",
            category = "Custom",
        )

        ApplicationManager.getApplication().invokeLater {
            val dialog = WrapperFormDialog(existingNames, initial = initial)
            if (!dialog.showAndGet()) return@invokeLater
            val wrapper = dialog.result ?: return@invokeLater
            val updated = existingCustom.toMutableList().apply { add(wrapper) }
            settings.customWrappersJson = WrapperJsonCodec.encodeList(updated)
            WrapIntentionRegistrar.syncRegistrations()
            Messages.showInfoMessage(
                project,
                "Wrapper \"${wrapper.name}\" created. It is now available in the Alt+Enter menu.",
                getFamilyName(),
            )
        }
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
