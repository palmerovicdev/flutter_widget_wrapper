package com.palmerodev.fww.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
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
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class WrapperSettingsConfigurable : Configurable {

    private val settings get() = FlutterWrapperSettings.getInstance()

    private val builtInCheckboxes = LinkedHashMap<String, JBCheckBox>()

    private val listModel = DefaultListModel<String>()
    private val wrapperList = JBList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
    }
    private val previewArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
    private val jsonArea = JBTextArea().apply {
        rows = 12
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = false
    }
    private val validationLabel = JBLabel()
    private var rootPanel: JPanel? = null

    private var currentWrappers: List<WidgetWrapper> = emptyList()
    private var suppressJsonListener = false
    private var suppressListSelection = false

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

        val help = JBLabel(FlutterWidgetWrapperBundle.message("settings.builtins.help"))
        help.border = JBUI.Borders.emptyBottom(6)
        panel.add(help, BorderLayout.NORTH)

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

        panel.add(buildDocsPanel(), BorderLayout.NORTH)
        panel.add(buildEditorSplit(), BorderLayout.CENTER)
        panel.add(buildValidationPanel(), BorderLayout.SOUTH)

        jsonArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onJsonChanged()
            override fun removeUpdate(e: DocumentEvent) = onJsonChanged()
            override fun changedUpdate(e: DocumentEvent) = onJsonChanged()
        })
        wrapperList.addListSelectionListener {
            if (it.valueIsAdjusting || suppressListSelection) return@addListSelectionListener
            updatePreview()
        }

        return panel
    }

    private fun buildDocsPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyBottom(6)

        val header = JBLabel("<html><b>${FlutterWidgetWrapperBundle.message("settings.custom.docs.header")}</b></html>")
        panel.add(header, BorderLayout.NORTH)

        val docs = JBLabel(DOCS_HTML)
        docs.verticalAlignment = JBLabel.TOP
        docs.border = JBUI.Borders.empty(4, 8, 4, 8)
        panel.add(docs, BorderLayout.CENTER)

        return panel
    }

    private fun buildEditorSplit(): JComponent {
        val splitter = JBSplitter(false, 0.28f)
        splitter.firstComponent = buildListPanel()
        splitter.secondComponent = buildRightPanel()
        splitter.preferredSize = Dimension(720, 420)
        return splitter
    }

    private fun buildListPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 4))
        panel.border = JBUI.Borders.emptyRight(6)

        val label = JBLabel(FlutterWidgetWrapperBundle.message("settings.custom.list.label"))
        panel.add(label, BorderLayout.NORTH)

        val scroll = JBScrollPane(wrapperList)
        scroll.preferredSize = Dimension(200, 300)
        panel.add(scroll, BorderLayout.CENTER)

        val actions = JPanel()
        actions.layout = BoxLayout(actions, BoxLayout.X_AXIS)
        val addBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.add"))
        addBtn.addActionListener { onAddWrapper() }
        val deleteBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.delete"))
        deleteBtn.addActionListener { onDeleteWrapper() }
        val previewBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.preview"))
        previewBtn.addActionListener { onOpenPreviewDialog() }
        actions.add(addBtn)
        actions.add(Box.createHorizontalStrut(4))
        actions.add(deleteBtn)
        actions.add(Box.createHorizontalStrut(4))
        actions.add(previewBtn)
        panel.add(actions, BorderLayout.SOUTH)

        return panel
    }

    private fun buildRightPanel(): JComponent {
        val split = JBSplitter(true, 0.4f)

        val previewPanel = JPanel(BorderLayout(0, 4))
        previewPanel.add(
            JBLabel(FlutterWidgetWrapperBundle.message("settings.custom.preview.label")),
            BorderLayout.NORTH,
        )
        previewPanel.add(JBScrollPane(previewArea), BorderLayout.CENTER)

        val jsonPanel = JPanel(BorderLayout(0, 4))
        jsonPanel.add(
            JBLabel(FlutterWidgetWrapperBundle.message("settings.custom.json.label")),
            BorderLayout.NORTH,
        )
        jsonPanel.add(JBScrollPane(jsonArea), BorderLayout.CENTER)

        val jsonButtons = JPanel()
        jsonButtons.layout = BoxLayout(jsonButtons, BoxLayout.X_AXIS)
        val importBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.import"))
        importBtn.addActionListener { onImport() }
        val exportBtn = JButton(FlutterWidgetWrapperBundle.message("settings.button.export"))
        exportBtn.addActionListener { onExport() }
        jsonButtons.add(Box.createHorizontalGlue())
        jsonButtons.add(importBtn)
        jsonButtons.add(Box.createHorizontalStrut(4))
        jsonButtons.add(exportBtn)
        jsonPanel.add(jsonButtons, BorderLayout.SOUTH)

        split.firstComponent = previewPanel
        split.secondComponent = jsonPanel
        return split
    }

    private fun buildValidationPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.border = JBUI.Borders.emptyTop(4)
        panel.add(validationLabel, BorderLayout.CENTER)
        return panel
    }

    private fun onAddWrapper() {
        val existing = currentNames()
        val dialog = WrapperFormDialog(existing)
        if (!dialog.showAndGet()) return
        val wrapper = dialog.result ?: return
        val updated = currentWrappers.toMutableList().apply { add(wrapper) }
        writeJson(WrapperJsonCodec.encodeList(updated))
        selectWrapperByName(wrapper.name)
    }

    private fun onDeleteWrapper() {
        val idx = wrapperList.selectedIndex
        if (idx < 0 || idx >= currentWrappers.size) return
        val victim = currentWrappers[idx]
        val parent = rootPanel ?: return
        val choice = Messages.showYesNoDialog(
            parent,
            "Delete wrapper \"${victim.name}\"?",
            FlutterWidgetWrapperBundle.message("settings.button.delete"),
            Messages.getQuestionIcon(),
        )
        if (choice != Messages.YES) return
        val updated = currentWrappers.toMutableList().apply { removeAt(idx) }
        writeJson(WrapperJsonCodec.encodeList(updated))
    }

    private fun onOpenPreviewDialog() {
        val builtIns = BuiltInWrappers.ALL.filter { it.name !in currentDisabled() }
        val customs = WrapperJsonCodec.parseList(jsonArea.text).filter(WrapperValidator::isValid)
        val all = LinkedHashMap<String, WidgetWrapper>().apply {
            for (w in builtIns) put(w.name, w)
            for (w in customs) put(w.name, w)
        }.values.toList()
        WrapperPreviewDialog(all).show()
    }

    private fun onImport() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withFileFilter { it.extension.equals("json", ignoreCase = true) }
        val parent = rootPanel ?: return
        val file = FileChooser.chooseFile(descriptor, parent, null, null) ?: return
        val content = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull()
            ?: return Messages.showErrorDialog(parent, "Could not read ${file.path}", "Import JSON")
        writeJson(content)
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

    private fun onJsonChanged() {
        if (suppressJsonListener) return
        revalidateJson()
        rebuildListFromJson()
    }

    private fun rebuildListFromJson() {
        val prevSelection = wrapperList.selectedValue
        currentWrappers = WrapperJsonCodec.parseList(jsonArea.text)
        suppressListSelection = true
        listModel.clear()
        for (w in currentWrappers) listModel.addElement(w.name)
        val newIdx = currentWrappers.indexOfFirst { it.name == prevSelection }
        if (newIdx >= 0) wrapperList.selectedIndex = newIdx
        else if (currentWrappers.isNotEmpty()) wrapperList.selectedIndex = 0
        suppressListSelection = false
        updatePreview()
    }

    private fun updatePreview() {
        val idx = wrapperList.selectedIndex
        if (idx < 0 || idx >= currentWrappers.size) {
            previewArea.text = FlutterWidgetWrapperBundle.message("settings.custom.list.empty")
            return
        }
        val w = currentWrappers[idx]
        val validation = WrapperValidator.validate(w)
        if (validation is WrapperValidator.Result.Invalid) {
            previewArea.text = "Invalid: ${validation.reason}"
            return
        }
        previewArea.text = WrapperTemplateEngine.apply(w, "Text('Hello')", "")
    }

    private fun writeJson(json: String) {
        suppressJsonListener = true
        try {
            jsonArea.text = json
        } finally {
            suppressJsonListener = false
        }
        revalidateJson()
        rebuildListFromJson()
    }

    private fun selectWrapperByName(name: String) {
        val idx = currentWrappers.indexOfFirst { it.name == name }
        if (idx >= 0) wrapperList.selectedIndex = idx
    }

    private fun revalidateJson() {
        val text = jsonArea.text
        if (text.isBlank() || text.trim() == "[]") {
            validationLabel.text = FlutterWidgetWrapperBundle.message("settings.custom.empty")
            validationLabel.foreground = JBColor.GRAY
            return
        }
        val wrappers = WrapperJsonCodec.parseList(text)
        if (wrappers.isEmpty()) {
            validationLabel.text = FlutterWidgetWrapperBundle.message(
                "settings.custom.invalid",
                "malformed JSON or empty array",
            )
            validationLabel.foreground = JBColor.RED
            return
        }
        for (w in wrappers) {
            val r = WrapperValidator.validate(w)
            if (r is WrapperValidator.Result.Invalid) {
                validationLabel.text = FlutterWidgetWrapperBundle.message(
                    "settings.custom.invalid",
                    "Invalid wrapper \"${w.name}\": ${r.reason}",
                )
                validationLabel.foreground = JBColor.RED
                return
            }
        }
        validationLabel.text = FlutterWidgetWrapperBundle.message("settings.custom.valid", wrappers.size)
        validationLabel.foreground = JBColor.foreground()
    }

    private fun currentNames(): Set<String> = currentWrappers.map { it.name }.toSet()

    override fun isModified(): Boolean {
        val normalized = normalizeStoredJson(settings.customWrappersJson)
        return jsonArea.text != normalized ||
            currentDisabled() != settings.disabledBuiltInNames
    }

    override fun apply() {
        val text = jsonArea.text.trim()
        settings.customWrappersJson = if (text == "[]") "" else jsonArea.text
        settings.disabledBuiltInNames = currentDisabled().toMutableSet()
        WrapIntentionRegistrar.syncRegistrations()
    }

    override fun reset() {
        val stored = normalizeStoredJson(settings.customWrappersJson)
        writeJson(stored)
        val disabled = settings.disabledBuiltInNames
        for ((name, cb) in builtInCheckboxes) cb.isSelected = name !in disabled
    }

    private fun normalizeStoredJson(stored: String): String =
        if (stored.isBlank()) "[]" else stored

    override fun disposeUIResources() {
        builtInCheckboxes.clear()
        rootPanel = null
    }

    private fun currentDisabled(): MutableSet<String> = builtInCheckboxes
        .filterValues { !it.isSelected }
        .keys
        .toMutableSet()

    companion object {
        private val DOCS_HTML = """
            <html>
            <body style='width: 640px'>
            A wrapper is a JSON object inside a top-level array (<code>[ ... ]</code>).
            Each entry describes a widget that can wrap another via the Alt+Enter menu.
            <br><br>
            <b>Required fields</b>
            <ul>
              <li><code>name</code> — unique name shown in the intention menu.</li>
              <li><code>template</code> — string or array of lines. Must contain
                  <code>&#36;{widget}</code>, which is replaced by the wrapped widget.</li>
            </ul>
            <b>Optional fields</b>
            <ul>
              <li><code>description</code> — short human description.</li>
              <li><code>category</code> — grouping label (default <code>Custom</code>).</li>
              <li><code>enabled</code> — boolean, default <code>true</code>.</li>
              <li><code>allowedParents</code> — list of parent widget names.
                  Use <code>["any"]</code> (default) or names like
                  <code>["Row","Column","Flex"]</code>.</li>
              <li><code>disallowedParents</code> — parents that block the wrapper.</li>
              <li><code>requiresDirectParent</code> — requires
                  <code>allowedParents</code> to be the immediate parent.</li>
              <li><code>warning</code> — tooltip warning shown to the user.</li>
            </ul>
            Use the <b>Add wrapper…</b> button below to fill a form instead of
            editing JSON by hand.
            </body>
            </html>
        """.trimIndent()
    }
}

internal class WrapperPreviewDialog(private val wrappers: List<WidgetWrapper>) :
    com.intellij.openapi.ui.DialogWrapper(true) {

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
