package com.palmerodev.fww.settings

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.CheckboxTree
import com.intellij.ui.CheckboxTreeBase
import com.intellij.ui.CheckedTreeNode
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.JBColor
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.TreeUIHelper
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.TabStops
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import com.palmerodev.fww.wrappers.WrapperTemplateEngine
import com.palmerodev.fww.wrappers.WrapperValidator
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.event.MouseEvent
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

    // toString() feeds the tree speed search.
    private class CategoryTag(val name: String) {
        override fun toString(): String = name
    }

    private class Entry(var wrapper: WidgetWrapper, val builtIn: Boolean, val overridesBuiltIn: Boolean = false) {
        override fun toString(): String = wrapper.name
    }

    private val builtInNames: Set<String> = BuiltInWrappers.ALL.mapTo(mutableSetOf()) { it.name }

    private val rootNode = CheckedTreeNode("root")
    private lateinit var tree: CheckboxTree
    private val treeModel get() = tree.model as DefaultTreeModel

    private val detailTitle = JBLabel()
    private val detailMeta = JBLabel()
    private val detailWarning = JBLabel(AllIcons.General.Warning)
    private val templateArea = DartCodeField.create(viewer = true)
    private val previewArea = DartCodeField.create(viewer = true)
    private val groupWrappersCheck = JBCheckBox(FlutterWidgetWrapperBundle.message("settings.option.group"))
    private val caretOnNameCheck = JBCheckBox(FlutterWidgetWrapperBundle.message("settings.option.caretOnName"))
    private var rootPanel: JComponent? = null

    override fun getDisplayName(): String = FlutterWidgetWrapperBundle.message("settings.title")

    override fun createComponent(): JComponent {
        tree = createTree()
        val decorator = ToolbarDecorator.createDecorator(tree)
            .setAddAction { onAdd() }
            .setEditAction { onEdit() }
            .setRemoveAction { onRemove() }
            .setEditActionUpdater { selectedEntry() != null }
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
        val options = JPanel().apply {
            layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
            border = JBUI.Borders.emptyTop(8)
            add(groupWrappersCheck)
            add(caretOnNameCheck)
        }
        val root = JPanel(BorderLayout()).apply {
            add(splitter, BorderLayout.CENTER)
            add(options, BorderLayout.SOUTH)
        }
        rootPanel = root
        reset()
        return root
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
                        if (obj.overridesBuiltIn) {
                            textRenderer.append("  ${FlutterWidgetWrapperBundle.message("settings.detail.overridesBuiltIn")}", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                        obj.wrapper.description?.takeIf { it.isNotBlank() }?.let {
                            textRenderer.append("  — $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                        }
                    }
                }
            }
        }
        // Explicit policy: the two-argument constructor is deprecated since 2026.2.
        val checkPolicy = CheckboxTreeBase.CheckPolicy(true, true, false, true)
        return object : CheckboxTree(renderer, rootNode, checkPolicy) {
            override fun onNodeStateChanged(node: CheckedTreeNode?) {
                recomputeFromTree()
            }
        }.apply {
            isRootVisible = false
            showsRootHandles = true
            selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
            addTreeSelectionListener { updateDetail() }
            TreeUIHelper.getInstance().installTreeSpeedSearch(this)
            object : DoubleClickListener() {
                override fun onDoubleClick(event: MouseEvent): Boolean {
                    if (selectedEntry() == null) return false
                    onEdit()
                    return true
                }
            }.installOn(this)
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
            add(detailMeta, BorderLayout.CENTER)
            add(detailWarning, BorderLayout.SOUTH)
        }
        panel.add(header, BorderLayout.NORTH)

        val body = JBSplitter(true, 0.6f).apply {
            firstComponent = labeledScroll("settings.detail.template", templateArea, withHelp = true)
            secondComponent = labeledScroll("settings.detail.preview", previewArea)
        }
        panel.add(body, BorderLayout.CENTER)
        return panel
    }

    private fun labeledScroll(
        labelKey: String,
        area: JComponent,
        withHelp: Boolean = false,
    ): JComponent =
        JPanel(BorderLayout(0, 4)).apply {
            val labelRow = JPanel(BorderLayout(6, 0)).apply {
                add(JBLabel(FlutterWidgetWrapperBundle.message(labelKey)), BorderLayout.WEST)
                if (withHelp) add(WrapperSyntaxHelp.createLabel(), BorderLayout.EAST)
            }
            add(labelRow, BorderLayout.NORTH)
            add(area, BorderLayout.CENTER)
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
        val customNames = customWrappers.mapTo(mutableSetOf()) { it.name }
        // A custom wrapper with a built-in's name replaces it, so only the override is listed.
        for (w in BuiltInWrappers.ALL) {
            if (w.name in customNames) continue
            grouped.getOrPut(w.category) { mutableListOf() }
                .add(Entry(w.copy(enabled = w.name !in disabledBuiltIns), builtIn = true))
        }
        for (w in customWrappers) {
            grouped.getOrPut(w.category) { mutableListOf() }
                .add(Entry(w, builtIn = false, overridesBuiltIn = w.name in builtInNames))
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
        // Overridden built-ins are not in the tree; keep their disabled flag for when the
        // override is deleted.
        val overridden = customWrappers.mapTo(mutableSetOf()) { it.name }
        val newDisabled = disabledBuiltIns.filterTo(mutableSetOf()) { it in overridden }
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
            detailWarning.isVisible = false
            return
        }
        val w = entry.wrapper
        detailTitle.text = w.name
        detailMeta.text = describe(entry)
        val warning = w.warning?.takeIf { it.isNotBlank() }
        detailWarning.text = warning?.let { FlutterWidgetWrapperBundle.message("settings.detail.warning", it) }
        detailWarning.isVisible = warning != null
        templateArea.text = w.template.joinToString("\n")
        val validation = WrapperValidator.validate(w)
        previewArea.text = if (validation is WrapperValidator.Result.Invalid) {
            FlutterWidgetWrapperBundle.message("settings.detail.invalid", validation.reason)
        } else {
            TabStops.stripToDefaults(WrapperTemplateEngine.apply(w, "Text('Hello')", ""))
        }
    }

    private fun describe(entry: Entry): String {
        val w = entry.wrapper
        val kind = when {
            entry.builtIn -> FlutterWidgetWrapperBundle.message("settings.tree.builtin")
            entry.overridesBuiltIn -> FlutterWidgetWrapperBundle.message("settings.detail.overridesBuiltIn")
            else -> FlutterWidgetWrapperBundle.message("settings.detail.custom")
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

    private fun categories(): List<String> =
        (BuiltInWrappers.ALL + customWrappers).map { it.category }.distinct()

    private fun customNames(): Set<String> = customWrappers.mapTo(mutableSetOf()) { it.name }

    private fun onAdd() {
        val dialog = WrapperFormDialog(customNames(), categories = categories())
        if (!dialog.showAndGet()) return
        val wrapper = dialog.result ?: return
        customWrappers.add(wrapper)
        rebuildTree(select = wrapper.name)
    }

    private fun onEdit() {
        val entry = selectedEntry() ?: return
        if (entry.builtIn) {
            onCustomizeBuiltIn(entry)
            return
        }
        val others = customNames() - entry.wrapper.name
        val dialog = WrapperFormDialog(others, initial = entry.wrapper, categories = categories())
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

    /** Editing a built-in saves a custom wrapper with the same name, which overrides it. */
    private fun onCustomizeBuiltIn(entry: Entry) {
        val dialog = WrapperFormDialog(customNames(), initial = entry.wrapper, categories = categories())
        if (!dialog.showAndGet()) return
        val updated = dialog.result ?: return
        customWrappers.add(updated.copy(enabled = entry.wrapper.enabled))
        rebuildTree(select = updated.name)
    }

    private fun onRemove() {
        val entry = selectedEntry() ?: return
        if (entry.builtIn) return
        val parent = rootPanel ?: return
        val choice = Messages.showYesNoDialog(
            parent,
            FlutterWidgetWrapperBundle.message(
                if (entry.overridesBuiltIn) "settings.delete.confirm.override" else "settings.delete.confirm",
                entry.wrapper.name,
            ),
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
        val title = FlutterWidgetWrapperBundle.message("settings.import.title")
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            .withExtensionFilter("json")
        val file = FileChooser.chooseFile(descriptor, parent, null, null) ?: return
        val content = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull()
            ?: return Messages.showErrorDialog(
                parent,
                FlutterWidgetWrapperBundle.message("settings.import.readError", file.path),
                title,
            )
        val parsed = WrapperJsonCodec.parseList(content)
        val imported = parsed.filter(WrapperValidator::isValid)
        if (imported.isEmpty()) {
            Messages.showErrorDialog(parent, FlutterWidgetWrapperBundle.message("settings.import.empty"), title)
            return
        }

        val existing = customWrappers.associateByTo(LinkedHashMap()) { it.name }
        val conflicts = imported.filter { it.name in existing }
        var replace = true
        if (conflicts.isNotEmpty()) {
            val choice = Messages.showYesNoCancelDialog(
                parent,
                FlutterWidgetWrapperBundle.message(
                    "settings.import.conflict",
                    conflicts.joinToString("\n") { "• ${it.name}" },
                ),
                title,
                FlutterWidgetWrapperBundle.message("settings.import.replace"),
                FlutterWidgetWrapperBundle.message("settings.import.keep"),
                Messages.getCancelButton(),
                Messages.getQuestionIcon(),
            )
            if (choice == Messages.CANCEL) return
            replace = choice == Messages.YES
        }

        var added = 0
        var replaced = 0
        var kept = 0
        for (w in imported) {
            when {
                w.name !in existing -> added++
                replace -> replaced++
                else -> {
                    kept++
                    continue
                }
            }
            existing[w.name] = w
        }
        customWrappers = existing.values.toMutableList()
        rebuildTree()
        Messages.showInfoMessage(
            parent,
            FlutterWidgetWrapperBundle.message("settings.import.summary", added, replaced, kept, parsed.size - imported.size),
            title,
        )
    }

    private fun onExport() {
        val parent = rootPanel ?: return
        val title = FlutterWidgetWrapperBundle.message("settings.export.title")
        val descriptor = FileSaverDescriptor(
            title,
            FlutterWidgetWrapperBundle.message("settings.export.description"),
            "json",
        )
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, parent)
        val baseDir = LocalFileSystem.getInstance().findFileByPath(System.getProperty("user.home"))
        val target = dialog.save(baseDir, "wrappers.json") ?: return
        runCatching { target.file.writeText(WrapperJsonCodec.encodeList(customWrappers)) }
            .onFailure {
                Messages.showErrorDialog(
                    parent,
                    FlutterWidgetWrapperBundle.message("settings.export.error", it.message.orEmpty()),
                    title,
                )
            }
    }

    // ---- Configurable contract ---------------------------------------------

    override fun isModified(): Boolean =
        disabledBuiltIns != settings.disabledBuiltInNames ||
            customWrappers != WrapperJsonCodec.parseList(settings.customWrappersJson) ||
            groupWrappersCheck.isSelected != settings.groupWrappers ||
            caretOnNameCheck.isSelected != settings.caretOnNameOnly

    override fun apply() {
        settings.disabledBuiltInNames = disabledBuiltIns.toMutableSet()
        settings.groupWrappers = groupWrappersCheck.isSelected
        settings.caretOnNameOnly = caretOnNameCheck.isSelected
        settings.customWrappersJson =
            if (customWrappers.isEmpty()) "" else WrapperJsonCodec.encodeList(customWrappers)
        WrapIntentionRegistrar.syncRegistrations()
    }

    override fun reset() {
        reloadModelFromSettings()
        groupWrappersCheck.isSelected = settings.groupWrappers
        caretOnNameCheck.isSelected = settings.caretOnNameOnly
        rebuildTree()
    }

    override fun disposeUIResources() {
        rootPanel = null
    }
}
