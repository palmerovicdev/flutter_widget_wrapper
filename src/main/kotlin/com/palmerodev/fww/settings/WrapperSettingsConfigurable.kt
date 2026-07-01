package com.palmerodev.fww.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import com.palmerodev.fww.wrappers.WrapperValidator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.GridLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class WrapperSettingsConfigurable : Configurable {

    private val settings get() = FlutterWrapperSettings.getInstance()

    private val builtInCheckboxes = LinkedHashMap<String, JBCheckBox>()
    private val jsonArea = JBTextArea().apply {
        rows = 15
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
    }
    private val validationLabel = JBLabel()
    private var rootPanel: JPanel? = null

    override fun getDisplayName(): String = FlutterWidgetWrapperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val root = JPanel()
        root.layout = BoxLayout(root, BoxLayout.Y_AXIS)
        root.border = JBUI.Borders.empty(10)

        root.add(buildBuiltInsSection())
        root.add(Box.createVerticalStrut(12))
        root.add(buildCustomSection())

        rootPanel = root
        reset()
        return root
    }

    private fun buildBuiltInsSection(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = BorderFactory.createTitledBorder(
            FlutterWidgetWrapperBundle.message("settings.builtins.header")
        )
        val grid = JPanel(GridLayout(0, 2, 8, 4))
        builtInCheckboxes.clear()
        for (w in BuiltInWrappers.ALL) {
            val cb = JBCheckBox(w.name)
            builtInCheckboxes[w.name] = cb
            grid.add(cb)
        }
        panel.add(grid, BorderLayout.CENTER)

        val actions = JPanel()
        actions.layout = BoxLayout(actions, BoxLayout.X_AXIS)
        val resetBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.reset"))
        resetBtn.addActionListener {
            for ((_, cb) in builtInCheckboxes) cb.isSelected = true
        }
        actions.add(resetBtn)
        actions.add(Box.createHorizontalGlue())
        panel.add(actions, BorderLayout.SOUTH)

        return panel
    }

    private fun buildCustomSection(): JComponent {
        val panel = JPanel(BorderLayout(0, 6))
        panel.border = BorderFactory.createTitledBorder(
            FlutterWidgetWrapperBundle.message("settings.custom.header")
        )

        jsonArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = revalidateJson()
            override fun removeUpdate(e: DocumentEvent) = revalidateJson()
            override fun changedUpdate(e: DocumentEvent) = revalidateJson()
        })
        val scroll = JBScrollPane(jsonArea)
        scroll.preferredSize = Dimension(600, 260)
        panel.add(scroll, BorderLayout.CENTER)

        val bottom = JPanel(BorderLayout())
        bottom.add(validationLabel, BorderLayout.CENTER)

        val actions = JPanel()
        actions.layout = BoxLayout(actions, BoxLayout.X_AXIS)
        val importBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.import"))
        importBtn.addActionListener { onImport() }
        val exportBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.export"))
        exportBtn.addActionListener { onExport() }
        val previewBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.preview"))
        previewBtn.addActionListener { onPreview() }
        actions.add(importBtn)
        actions.add(Box.createHorizontalStrut(6))
        actions.add(exportBtn)
        actions.add(Box.createHorizontalStrut(6))
        actions.add(previewBtn)
        bottom.add(actions, BorderLayout.EAST)
        panel.add(bottom, BorderLayout.SOUTH)

        return panel
    }

    private fun onImport() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension.equals("json", ignoreCase = true) }
        val parent = rootPanel ?: return
        val file = FileChooser.chooseFile(descriptor, parent, null, null) ?: return
        val content = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull()
            ?: return Messages.showErrorDialog(parent, "Could not read ${file.path}", "Import JSON")
        jsonArea.text = content
    }

    private fun onExport() {
        val parent = rootPanel ?: return
        val descriptor = FileSaverDescriptor("Export wrappers", "Save custom wrappers as JSON", "json")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, parent)
        val baseDir = LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home"))
        val target = dialog.save(baseDir, "wrappers.json") ?: return
        runCatching { target.file.writeText(jsonArea.text) }
            .onFailure {
                Messages.showErrorDialog(parent, "Could not write file: ${it.message}", "Export JSON")
            }
    }

    private fun onPreview() {
        val builtIns = BuiltInWrappers.ALL.filter { it.name !in currentDisabled() }
        val customs = WrapperJsonCodec.parseList(jsonArea.text).filter(WrapperValidator::isValid)
        val all = LinkedHashMap<String, WidgetWrapper>().apply {
            for (w in builtIns) put(w.name, w)
            for (w in customs) put(w.name, w)
        }.values.toList()
        WrapperPreviewDialog(all).show()
    }

    private fun revalidateJson() {
        val text = jsonArea.text
        if (text.isBlank()) {
            validationLabel.text = FlutterWidgetWrapperBundle.message("settings.custom.empty")
            validationLabel.foreground = JBColor.GRAY
            return
        }
        val wrappers = WrapperJsonCodec.parseList(text)
        if (wrappers.isEmpty()) {
            validationLabel.text = FlutterWidgetWrapperBundle.message(
                "settings.custom.invalid",
                "malformed JSON or empty array"
            )
            validationLabel.foreground = JBColor.RED
            return
        }
        for (w in wrappers) {
            val r = WrapperValidator.validate(w)
            if (r is WrapperValidator.Result.Invalid) {
                validationLabel.text = FlutterWidgetWrapperBundle.message(
                    "settings.custom.invalid",
                    "Invalid wrapper \"${w.name}\": ${r.reason}"
                )
                validationLabel.foreground = JBColor.RED
                return
            }
        }
        validationLabel.text = FlutterWidgetWrapperBundle.message("settings.custom.valid", wrappers.size)
        validationLabel.foreground = JBColor.foreground()
    }

    override fun isModified(): Boolean {
        return jsonArea.text != settings.customWrappersJson ||
            currentDisabled() != settings.disabledBuiltInNames
    }

    override fun apply() {
        settings.customWrappersJson = jsonArea.text
        settings.disabledBuiltInNames = currentDisabled().toMutableSet()
        WrapIntentionRegistrar.syncRegistrations()
    }

    override fun reset() {
        jsonArea.text = settings.customWrappersJson
        val disabled = settings.disabledBuiltInNames
        for ((name, cb) in builtInCheckboxes) cb.isSelected = name !in disabled
        revalidateJson()
    }

    override fun disposeUIResources() {
        builtInCheckboxes.clear()
        rootPanel = null
    }

    private fun currentDisabled(): MutableSet<String> = builtInCheckboxes
        .filterValues { !it.isSelected }
        .keys
        .toMutableSet()
}

private class WrapperPreviewDialog(private val wrappers: List<WidgetWrapper>) : DialogWrapper(true) {

    private val list = JBList(wrappers.map { it.name })
    private val preview = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }

    init {
        title = FlutterWidgetWrapperBundle.message("settings.preview.title")
        init()
        list.addListSelectionListener { updatePreview() }
        if (wrappers.isNotEmpty()) list.selectedIndex = 0
        updatePreview()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(8, 0))
        root.preferredSize = Dimension(600, 380)
        val leftScroll = JBScrollPane(list)
        leftScroll.preferredSize = Dimension(180, 380)
        root.add(leftScroll, BorderLayout.WEST)
        root.add(JBScrollPane(preview), BorderLayout.CENTER)
        return root
    }

    private fun updatePreview() {
        val idx = list.selectedIndex
        if (idx < 0 || idx >= wrappers.size) {
            preview.text = FlutterWidgetWrapperBundle.message("settings.preview.no.selection")
            return
        }
        val wrapper = wrappers[idx]
        preview.text = WrapperTemplateEngine.apply(wrapper, "Text('Hello')", "")
    }
}
