package com.palmerodev.fww.settings

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.palmerodev.fww.model.WidgetWrapper
import javax.swing.JComponent

/** Lets the user tick wrappers from a list (presets to add, custom wrappers to export). */
class WrapperPickerDialog(
    title: String,
    private val wrappers: List<WidgetWrapper>,
    checked: (WidgetWrapper) -> Boolean,
) : DialogWrapper(true) {

    private val list = CheckBoxList<WidgetWrapper>().apply {
        for (w in wrappers) {
            val label = w.description?.takeIf { it.isNotBlank() }?.let { "${w.name}  —  $it" } ?: w.name
            addItem(w, "$label   [${w.category}]", checked(w))
        }
    }

    val selected: List<WidgetWrapper>
        get() = wrappers.filter { list.isItemSelected(it) }

    init {
        this.title = title
        init()
    }

    override fun createCenterPanel(): JComponent =
        JBScrollPane(list).apply { preferredSize = JBUI.size(520, 420) }
}
