package com.palmerodev.fww.settings

import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
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
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class WrapperSettingsConfigurable : Configurable {

    private val settings get() = FlutterWrapperSettings.getInstance()

    /** In-memory editing state, mirrored back to [settings] on [apply]. */
    private val disabledBuiltIns = mutableSetOf<String>()
    private var customWrappers = mutableListOf<WidgetWrapper>()

    private class CategoryTag(val name: String)
    private class Entry(var wrapper: WidgetWrapper, val builtIn: Boolean)

    private val rootNode = CheckedTreeNode("root")
    private lateinit var tree: CheckboxTree
    private val treeModel get() = tree.model as DefaultTreeModel

    private val detailTitle = JBLabel()
    private val detailMeta = JBLabel()
    private val templateArea = readOnlyCode()
    private val previewArea = readOnlyCode()
    private var rootPanel: JComponent? = null

    override fun getDisplayName(): String = FlutterWidgetWrapperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        tree = createTree()
        val decorator = ToolbarDecorator.createDecorator(tree)
            .setAddAction { onAdd() }
            .setEditAction { onEdit() }
            .setRemoveAction { onRemove() }
            .setEditActionUpdater { isCustomSelected() }
            .setRemoveActionUpdater { isCustomSelected() }
            .setAddActionName(FlutterWidgetWrapperBundle.message("settings.button.add"))
            .disableUpDownActions()

        val leftPanel = JPanel(BorderLayout()).apply {
            add(decorator.createPanel(), BorderLayout.CENTER)
            add(buildSecondaryActions(), BorderLayout.SOUTH)
        }

        val splitter = JBSplitter(false, 0.42f).apply {
            firstComponent = leftPanel
            secondComponent = buildDetailPanel()
            preferredSize = Dimension(780, 480)
        }
        rootPanel = splitter
        reset()
        return splitter
    }

    private fun createTree(): CheckboxTree {
        val renderer = object : CheckboxTree.CheckboxTreeCellRenderer() {
            override fun customizeRenderer(
                tree: JTree, value: Any, selected: Boolean, expanded: Boolean,
                leaf: Boolean, row: Int, hasFocus: Boolean,
            ) {
                val node = value as? CheckedTreeNode ?: return
                when (val obj = node.userObject) {
                    is CategoryTag ->
                        textRenderer.append(obj.name, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                    is Entry -> {
                        textRenderer.append(obj.wrapper.name)
                        if (obj.builtIn) {
                            textRenderer.append("  ${FlutterWidgetWrapperBundle.message("settings.tree.builtin")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                        obj.wrapper.description?.takeIf { it.isNotBlank() }?.let {
                            textRenderer.append("  — $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                    }
                }
            }
        }
        return object : CheckboxTree(renderer, rootNode) {
            override fun onNodeStateChanged(node: CheckedTreeNode?) {
                recomputeFromTree()
            }
        }.apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addTreeSelectionListener { updateDetail() }
        }
    }

    private fun buildSecondaryActions(): JComponent {
        val panel = JPanel().apply { border = JBUI.Borders.emptyTop(6) }
        panel.layout = javax.swing.BoxLayout(panel, javax.swing.BoxLayout.X_AXIS)

        fun button(key: String, action: () -> Unit) = JButton(FlutterWidgetWrapperBundle.message(key)).apply {
            addActionListener { action() }
        }
        panel.add(button("settings.button.duplicate") { onDuplicate() })
        panel.add(javax.swing.Box.createHorizontalGlue())
        panel.add(button("settings.button.import") { onImport() })
        panel.add(javax.swing.Box.createHorizontalStrut(4))
        panel.add(button("settings.button.export") { onExport() })
        return panel
    }

    private fun buildDetailPanel(): JComponent {
        val panel = JPanel(BorderLayout(0, 6)).apply { border = JBUI.Borders.emptyLeft(8) }

        val header = JPanel(BorderLayout()).apply {
            detailTitle.font = detailTitle.font.deriveFont(Font.BOLD, detailTitle.font.size + 2f)
            add(detailTitle, BorderLayout.NORTH)
            detailMeta.foreground = JBColor.GRAY
            add(detailMeta, BorderLayout.SOUTH)
        }
        panel.add(header, BorderLayout.NORTH)

        val body = JBSplitter(true, 0.6f).apply {
            firstComponent = labeledScroll("settings.detail.template", templateArea)
            secondComponent = labeledScroll("settings.detail.preview", previewArea)
        }
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    private fun labeledScroll(labelKey: String, area: JBTextArea): JComponent =
        JPanel(BorderLayout(0, 4)).apply {
            add(JBLabel(FlutterWidgetWrapperBundle.message(labelKey)), BorderLayout.NORTH)
            add(JBScrollPane(area), BorderLayout.CENTER)
        }

    // ---- model <-> tree ----------------------------------------------------

    private fun reloadModelFromSettings() {
        disabledBuiltIns.clear()
        disabledBuiltIns.addAll(settings.disabledBuiltInNames)
        customWrappers = WrapperJsonCodec.parseList(settings.customWrappersJson).toMutableList()
    }

    private fun rebuildTree(select: String? = null) {
        rootNode.removeAllChildren()
        val grouped = LinkedHashMap<String, MutableList<Entry>>()
        for (w in BuiltInWrappers.ALL) {
            grouped.getOrPut(w.category) { mutableListOf() }
                .add(Entry(w.copy(enabled = w.name !in disabledBuiltIns), builtIn = true))
        }
        for (w in customWrappers) {
            grouped.getOrPut(w.category) { mutableListOf() }.add(Entry(w, builtIn = false))
        }
        for ((category, entries) in grouped) {
            val categoryNode = CheckedTreeNode(CategoryTag(category))
            for (entry in entries) {
                val leaf = CheckedTreeNode(entry)
                leaf.isChecked = entry.wrapper.enabled
                categoryNode.add(leaf)
            }
            categoryNode.isChecked = entries.all { it.wrapper.enabled }
            rootNode.add(categoryNode)
        }
        treeModel.reload()
        TreeUtil.expandAll(tree)
        if (select != null) selectByName(select) else updateDetail()
    }

    private fun recomputeFromTree() {
        val newDisabled = mutableSetOf<String>()
        val enabledByName = mutableMapOf<String, Boolean>()
        for (categoryNode in rootNode.children().toList().filterIsInstance<CheckedTreeNode>()) {
            for (leaf in categoryNode.children().toList().filterIsInstance<CheckedTreeNode>()) {
                val entry = leaf.userObject as? Entry ?: continue
                // Keep Entry.wrapper in sync so Edit/Duplicate/detail see the checkbox state.
                entry.wrapper = entry.wrapper.copy(enabled = leaf.isChecked)
                if (entry.builtIn) {
                    if (!leaf.isChecked) newDisabled.add(entry.wrapper.name)
                } else {
                    enabledByName[entry.wrapper.name] = leaf.isChecked
                }
            }
        }
        disabledBuiltIns.clear()
        disabledBuiltIns.addAll(newDisabled)
        customWrappers = customWrappers
            .map { it.copy(enabled = enabledByName[it.name] ?: it.enabled) }
            .toMutableList()
        updateDetail()
    }

    private fun selectedEntry(): Entry? {
        val node = tree.selectionPath?.lastPathComponent as? CheckedTreeNode ?: return null
        return node.userObject as? Entry
    }

    private fun isCustomSelected(): Boolean = selectedEntry()?.builtIn == false

    private fun selectByName(name: String) {
        for (categoryNode in rootNode.children().toList().filterIsInstance<CheckedTreeNode>()) {
            for (leaf in categoryNode.children().toList().filterIsInstance<CheckedTreeNode>()) {
                val entry = leaf.userObject as? Entry
                if (entry?.wrapper?.name == name) {
                    TreeUtil.selectNode(tree, leaf)
                    return
                }
            }
        }
        updateDetail()
    }

    private fun updateDetail() {
        val entry = selectedEntry()
        if (entry == null) {
            detailTitle.text = FlutterWidgetWrapperBundle.message("settings.detail.none")
            detailMeta.text = " "
            templateArea.text = ""
            previewArea.text = ""
            return
        }
        val w = entry.wrapper
        detailTitle.text = w.name
        detailMeta.text = describe(entry)
        templateArea.text = w.template.joinToString("\n")
        val validation = WrapperValidator.validate(w)
        previewArea.text = if (validation is WrapperValidator.Result.Invalid) {
            FlutterWidgetWrapperBundle.message("settings.detail.invalid", validation.reason)
        } else {
            WrapperTemplateEngine.apply(w, "Text('Hello')", "")
        }
    }

    private fun describe(entry: Entry): String {
        val w = entry.wrapper
        val kind = if (entry.builtIn) {
            FlutterWidgetWrapperBundle.message("settings.tree.builtin")
        } else {
            FlutterWidgetWrapperBundle.message("settings.detail.custom")
        }
        val scope = when {
            w.requiresDirectParent -> FlutterWidgetWrapperBundle.message("settings.detail.directParent", w.allowedParents.joinToString(", "))
            w.allowedParents == listOf("any") -> FlutterWidgetWrapperBundle.message("settings.detail.anyParent")
            else -> FlutterWidgetWrapperBundle.message("settings.detail.inside", w.allowedParents.joinToString(", "))
        }
        val state = if (w.enabled) "" else "  ·  ${FlutterWidgetWrapperBundle.message("settings.detail.disabled")}"
        return "$kind  ·  ${w.category}  ·  $scope$state"
    }

    // ---- toolbar actions ---------------------------------------------------

    private fun allNames(): Set<String> =
        (BuiltInWrappers.ALL.map { it.name } + customWrappers.map { it.name }).toSet()

    private fun onAdd() {
        val dialog = WrapperFormDialog(allNames())
        if (!dialog.showAndGet()) return
        val wrapper = dialog.result ?: return
        customWrappers.add(wrapper)
        rebuildTree(select = wrapper.name)
    }

    private fun onEdit() {
        val entry = selectedEntry() ?: return
        if (entry.builtIn) return
        val others = allNames() - entry.wrapper.name
        val dialog = WrapperFormDialog(others, initial = entry.wrapper)
        if (!dialog.showAndGet()) return
        val updated = dialog.result ?: return
        val idx = customWrappers.indexOfFirst { it.name == entry.wrapper.name }
        if (idx >= 0) {
            // Prefer the list model (kept in sync by recomputeFromTree) over a stale Entry.
            val enabled = customWrappers[idx].enabled
            customWrappers[idx] = updated.copy(enabled = enabled)
        }
        rebuildTree(select = updated.name)
    }

    private fun onRemove() {
        val entry = selectedEntry() ?: return
        if (entry.builtIn) return
        val parent = rootPanel ?: return
        val choice = Messages.showYesNoDialog(
            parent,
            FlutterWidgetWrapperBundle.message("settings.delete.confirm", entry.wrapper.name),
            FlutterWidgetWrapperBundle.message("settings.button.delete"),
            Messages.getQuestionIcon(),
        )
        if (choice != Messages.YES) return
        customWrappers.removeAll { it.name == entry.wrapper.name }
        rebuildTree()
    }

    private fun onDuplicate() {
        val entry = selectedEntry() ?: return
        val base = entry.wrapper
        val copy = base.copy(
            name = uniqueName(base.name),
            category = if (entry.builtIn) "Custom" else base.category,
            enabled = true,
        )
        customWrappers.add(copy)
        rebuildTree(select = copy.name)
    }

    private fun uniqueName(base: String): String {
        val taken = allNames()
        if ("${base}Copy" !in taken) return "${base}Copy"
        var i = 2
        while ("${base}Copy$i" in taken) i++
        return "${base}Copy$i"
    }

    private fun onImport() {
        val parent = rootPanel ?: return
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withExtensionFilter("json")
        val file = FileChooser.chooseFile(descriptor, parent, null, null) ?: return
        val content = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull()
            ?: return Messages.showErrorDialog(parent, "Could not read ${file.path}", "Import JSON")
        val imported = WrapperJsonCodec.parseList(content).filter(WrapperValidator::isValid)
        if (imported.isEmpty()) {
            Messages.showErrorDialog(parent, FlutterWidgetWrapperBundle.message("settings.import.empty"), "Import JSON")
            return
        }
        val existing = customWrappers.associateBy { it.name }.toMutableMap()
        for (w in imported) existing[w.name] = w
        customWrappers = existing.values.toMutableList()
        rebuildTree()
    }

    private fun onExport() {
        val parent = rootPanel ?: return
        val descriptor = FileSaverDescriptor("Export wrappers", "Save custom wrappers as JSON", "json")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, parent)
        val baseDir = LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home"))
        val target = dialog.save(baseDir, "wrappers.json") ?: return
        runCatching { target.file.writeText(WrapperJsonCodec.encodeList(customWrappers)) }
            .onFailure { Messages.showErrorDialog(parent, "Could not write file: ${it.message}", "Export JSON") }
    }

    // ---- Configurable contract ---------------------------------------------

    override fun isModified(): Boolean =
        disabledBuiltIns != settings.disabledBuiltInNames ||
            customWrappers != WrapperJsonCodec.parseList(settings.customWrappersJson)

    override fun apply() {
        settings.disabledBuiltInNames = disabledBuiltIns.toMutableSet()
        settings.customWrappersJson =
            if (customWrappers.isEmpty()) "" else WrapperJsonCodec.encodeList(customWrappers)
        WrapIntentionRegistrar.syncRegistrations()
    }

    override fun reset() {
        reloadModelFromSettings()
        rebuildTree()
    }

    override fun disposeUIResources() {
        rootPanel = null
    }

    private fun readOnlyCode() = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
    }
}
