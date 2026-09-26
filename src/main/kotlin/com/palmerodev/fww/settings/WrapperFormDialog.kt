package com.palmerodev.fww.settings

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.TextFieldWithAutoCompletion
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.model.ChildKind
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.TabStops
import com.palmerodev.fww.wrappers.TemplateLint
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Create/edit form for a custom wrapper.
 *
 * [takenNames] are the custom wrappers the name must not collide with. A built-in name is
 * allowed and turns the wrapper into an override of that built-in (with a warning).
 */
class WrapperFormDialog(
    private val takenNames: Set<String>,
    private val initial: WidgetWrapper? = null,
    private val allowNameChange: Boolean = true,
    private val builtInNames: Set<String> = BuiltInWrappers.ALL.mapTo(mutableSetOf()) { it.name },
    categories: Collection<String> = BuiltInWrappers.ALL.map { it.category },
) : DialogWrapper(true) {

    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val categoryField = ComboBox((categories + "Custom").distinct().sorted().toTypedArray()).apply {
        isEditable = true
    }
    private val templateArea = DartCodeField.create(viewer = false)
    private val previewArea = DartCodeField.create(viewer = true)
    private val allowedField = parentsField()
    private val disallowedField = parentsField()
    private val requiresDirectParentCheck = JBCheckBox()
    private val warningField = JBTextField()
    private val slotsField = JBTextField()
    private val childKindField = ComboBox(ChildKind.ALL.toTypedArray())

    var result: WidgetWrapper? = null
        private set

    init {
        title = FlutterWidgetWrapperBundle.message(
            if (initial == null) "settings.form.title" else "settings.form.title.edit",
        )
        initial?.let { populate(it) }
        nameField.isEnabled = allowNameChange
        templateArea.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) = updatePreview()
        })
        updatePreview()
        init()
    }

    private val categoryText: String
        get() = categoryField.editor.item?.toString()?.trim().orEmpty()

    /** Live preview of the template applied to `Text('Hello')`, tab-stops at their defaults. */
    private fun updatePreview() {
        val lines = templateArea.text.split('\n')
        previewArea.text = if (lines.none { it.contains($$"${widget}") }) {
            FlutterWidgetWrapperBundle.message("settings.form.error.template")
        } else {
            renderPreview()
        }
    }

    private fun renderPreview(): String {
        val lines = templateArea.text.split('\n')
        val sample = WidgetWrapper(name = "preview", template = lines.map(TabStops::stripToDefaults))
        return WrapperTemplateEngine.apply(sample, "Text('Hello')", "")
    }

    private fun populate(w: WidgetWrapper) {
        nameField.text = w.name
        descriptionField.text = w.description.orEmpty()
        categoryField.editor.item = w.category
        templateArea.text = w.template.joinToString("\n")
        allowedField.text = w.allowedParents.joinToString(", ")
        disallowedField.text = w.disallowedParents.joinToString(", ")
        requiresDirectParentCheck.isSelected = w.requiresDirectParent
        warningField.text = w.warning.orEmpty()
        slotsField.text = w.allowedSlots.joinToString(", ")
        childKindField.item = w.childKind
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        panel.border = JBUI.Borders.empty(8)
        panel.preferredSize = Dimension(660, 760)

        var row = 0
        addRow(panel, row++, "settings.form.label.name", nameField)
        addRow(panel, row++, "settings.form.label.description", descriptionField)
        addRow(panel, row++, "settings.form.label.category", categoryField)

        templateArea.preferredSize = Dimension(520, 200)
        addRow(
            panel,
            row++,
            "settings.form.label.template",
            templateArea,
            fillVertical = true,
            withHelp = true,
        )

        val hint = JBLabel(FlutterWidgetWrapperBundle.message("settings.form.hint.template"))
        val hintGbc = GridBagConstraints().apply {
            gridx = 1
            gridy = row++
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            insets = JBUI.insets(0, 4, 8, 0)
        }
        panel.add(hint, hintGbc)

        addRow(panel, row++, "settings.form.label.allowedParents", allowedField)
        addRow(panel, row++, "settings.form.label.disallowedParents", disallowedField)
        addRow(panel, row++, "settings.form.label.requiresDirectParent", requiresDirectParentCheck)
        addRow(panel, row++, "settings.form.label.warning", warningField)
        addRow(panel, row++, "settings.form.label.slots", slotsField)
        addRow(panel, row++, "settings.form.label.childKind", childKindField)

        previewArea.preferredSize = Dimension(520, 140)
        addRow(panel, row++, "settings.detail.preview", previewArea, fillVertical = true)

        if (categoryText.isBlank()) categoryField.editor.item = "Custom"
        if (allowedField.text.isBlank()) allowedField.text = "any"
        return panel
    }

    private fun addRow(
        panel: JPanel,
        row: Int,
        labelKey: String,
        component: JComponent,
        fillVertical: Boolean = false,
        withHelp: Boolean = false,
    ) {
        val labelGbc = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            anchor = GridBagConstraints.NORTHWEST
            insets = JBUI.insets(4, 4, 4, 8)
        }
        val label = if (withHelp) {
            JPanel(BorderLayout(4, 0)).apply {
                add(JBLabel(FlutterWidgetWrapperBundle.message(labelKey)), BorderLayout.WEST)
                add(WrapperSyntaxHelp.createLabel(), BorderLayout.EAST)
            }
        } else {
            JBLabel(FlutterWidgetWrapperBundle.message(labelKey))
        }
        panel.add(label, labelGbc)

        val fieldGbc = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            weightx = 1.0
            fill = if (fillVertical) GridBagConstraints.BOTH else GridBagConstraints.HORIZONTAL
            if (fillVertical) weighty = 1.0
            insets = JBUI.insets(4, 0, 4, 4)
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
        if (name != originalName && name in takenNames) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.error.duplicate", name),
                nameField,
            )
        }
        if (!templateArea.text.contains($$"${widget}")) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.error.template"),
                templateArea,
            )
        }
        TemplateLint.check(templateArea.text.split('\n')).firstOrNull()?.let { problem ->
            return ValidationInfo(lintMessage(problem), templateArea)
        }
        if (DartSyntax.hasErrors(renderPreview())) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.warning.syntax"),
                templateArea,
            ).asWarning().withOKEnabled()
        }
        if (name in builtInNames) {
            return ValidationInfo(
                FlutterWidgetWrapperBundle.message("settings.form.warning.overridesBuiltIn", name),
                nameField,
            ).asWarning().withOKEnabled()
        }
        return null
    }

    override fun doOKAction() {
        result = WidgetWrapper(
            name = nameField.text.trim(),
            description = descriptionField.text.trim().ifBlank { null },
            category = categoryText.ifBlank { "Custom" },
            template = templateArea.text.split('\n'),
            allowedParents = parseList(allowedField.text).ifEmpty { listOf("any") },
            disallowedParents = parseList(disallowedField.text),
            requiresDirectParent = requiresDirectParentCheck.isSelected,
            warning = warningField.text.trim().ifBlank { null },
            enabled = initial?.enabled ?: true,
            allowedSlots = parseList(slotsField.text),
            childKind = childKindField.item ?: ChildKind.ANY,
        )
        super.doOKAction()
    }

    private fun lintMessage(problem: TemplateLint.Problem): String = when (problem) {
        is TemplateLint.Problem.MalformedMarker ->
            FlutterWidgetWrapperBundle.message("settings.form.error.marker", problem.line)
        is TemplateLint.Problem.ReservedWithDefault ->
            FlutterWidgetWrapperBundle.message("settings.form.error.reserved", problem.name)
        is TemplateLint.Problem.Unbalanced ->
            FlutterWidgetWrapperBundle.message("settings.form.error.unbalanced", problem.bracket)
    }

    /** Comma-separated widget names with completion of common Flutter parents. */
    private fun parentsField(): TextFieldWithAutoCompletion<String> {
        val provider = object : TextFieldWithAutoCompletion.StringsCompletionProvider(KNOWN_PARENTS, null) {
            override fun getPrefix(text: String, offset: Int): String =
                text.substring(0, offset).substringAfterLast(',').trimStart()
        }
        return TextFieldWithAutoCompletion(ProjectManager.getInstance().defaultProject, provider, false, "")
    }

    private fun parseList(text: String): List<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    override fun getPreferredFocusedComponent(): JComponent =
        if (allowNameChange) nameField else templateArea
}

private val KNOWN_PARENTS = listOf(
    "any", "Row", "Column", "Flex", "Stack", "Wrap", "ListView", "GridView", "CustomScrollView",
    "Scaffold", "Container", "Padding", "Center", "Align", "SizedBox", "Card", "Material",
    "Expanded", "Flexible", "Positioned", "SafeArea", "SingleChildScrollView", "IndexedStack",
    "PageView", "TabBarView", "Table", "Form", "AppBar", "Dialog", "AlertDialog", "InkWell",
    "GestureDetector", "Opacity", "Transform", "ClipRRect", "DecoratedBox", "AnimatedContainer",
    "Hero", "LayoutBuilder", "Builder", "Theme", "MediaQuery", "Navigator",
)
