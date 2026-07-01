package com.palmerodev.fww.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.model.WidgetWrapper
import java.awt.Dimension
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class WrapperFormDialog(
    private val existingNames: Set<String>,
    private val initial: WidgetWrapper? = null,
    private val allowNameChange: Boolean = true,
) : DialogWrapper(true) {

    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val categoryField = JBTextField()
    private val templateArea = JBTextArea().apply {
        rows = 10
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val allowedField = JBTextField()
    private val disallowedField = JBTextField()
    private val requiresDirectParentCheck = JBCheckBox()
    private val warningField = JBTextField()

    var result: WidgetWrapper? = null
        private set

    init {
        title = FlutterWidgetWrapperBundle.message(
            if (initial == null) "settings.form.title" else "settings.form.title.edit",
        )
        initial?.let { populate(it) }
        nameField.isEnabled = allowNameChange
        init()
    }

    private fun populate(w: WidgetWrapper) {
        nameField.text = w.name
        descriptionField.text = w.description.orEmpty()
        categoryField.text = w.category
        templateArea.text = w.template.joinToString("\n")
        allowedField.text = w.allowedParents.joinToString(", ")
        disallowedField.text = w.disallowedParents.joinToString(", ")
        requiresDirectParentCheck.isSelected = w.requiresDirectParent
        warningField.text = w.warning.orEmpty()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        panel.preferredSize = Dimension(560, 520)

        var row = 0
        addRow(panel, row++, "settings.form.label.name", nameField)
        addRow(panel, row++, "settings.form.label.description", descriptionField)
        addRow(panel, row++, "settings.form.label.category", categoryField)

        val templateScroll = JBScrollPane(templateArea)
        templateScroll.preferredSize = Dimension(520, 200)
        addRow(panel, row++, "settings.form.label.template", templateScroll, fillVertical = true)

        val hint = JBLabel(FlutterWidgetWrapperBundle.message("settings.form.hint.template"))
        val hintGbc = GridBagConstraints().apply {
            gridx = 1
            gridy = row++
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = Insets(0, 4, 8, 0)
        }
        panel.add(hint, hintGbc)

        addRow(panel, row++, "settings.form.label.allowedParents", allowedField)
        addRow(panel, row++, "settings.form.label.disallowedParents", disallowedField)
        addRow(panel, row++, "settings.form.label.requiresDirectParent", requiresDirectParentCheck)
        addRow(panel, row++, "settings.form.label.warning", warningField)

        if (categoryField.text.isBlank()) categoryField.text = "Custom"
        if (allowedField.text.isBlank()) allowedField.text = "any"
        return panel
    }

    private fun addRow(
        panel: JPanel,
        row: Int,
        labelKey: String,
        component: JComponent,
        fillVertical: Boolean = false,
    ) {
        val labelGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.NORTHWEST
            insets = Insets(4, 4, 4, 8)
        }
        panel.add(JBLabel(FlutterWidgetWrapperBundle.message(labelKey)), labelGbc)

        val fieldGbc = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = if (fillVertical) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
            if (fillVertical) weighty = 1.0
            insets = Insets(4, 0, 4, 4)
        }
        panel.add(component, fieldGbc)
    }

    override fun doValidate(): ValidationInfo? {
        val name = nameField.text.trim()
        if (name.isBlank()) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.error.name"),
                nameField,
            )
        }
        val originalName = initial?.name
        if (name != originalName && name in existingNames) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.error.duplicate", name),
                nameField,
            )
        }
        if (!templateArea.text.contains("\${widget}")) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.error.template"),
                templateArea,
            )
        }
        return null
    }

    override fun doOKAction() {
        result = WidgetWrapper(
            name = nameField.text.trim(),
            description = descriptionField.text.trim().ifBlank { null },
            category = categoryField.text.trim().ifBlank { "Custom" },
            template = templateArea.text.split('\n'),
            allowedParents = parseList(allowedField.text).ifEmpty { listOf("any") },
            disallowedParents = parseList(disallowedField.text),
            requiresDirectParent = requiresDirectParentCheck.isSelected,
            warning = warningField.text.trim().ifBlank { null },
            enabled = initial?.enabled ?: true,
        )
        super.doOKAction()
    }

    private fun parseList(text: String): List<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    override fun getPreferredFocusedComponent(): JComponent =
        if (allowNameChange) nameField else templateArea
}
