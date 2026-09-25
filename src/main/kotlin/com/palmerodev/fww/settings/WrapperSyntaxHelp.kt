package com.palmerodev.fww.settings

import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.HTMLEditorKitBuilder
import com.intellij.util.ui.JBUI
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JEditorPane
import javax.swing.ScrollPaneConstants

/**
 * Shared "template syntax" help.
 *
 * A hover tooltip is deliberately *not* used: the reference is far taller than the
 * tooltip's max size, so the popup ended up under the mouse pointer and flickered
 * open/closed. A click-triggered, scrollable popup shows the whole text instead.
 */
object WrapperSyntaxHelp {

    private val POPUP_SIZE = Dimension(520, 380)

    fun createLabel(): JComponent =
        ActionLink(FlutterWidgetWrapperBundle.message("settings.help.link")) { event ->
            val source = event.source as? JComponent ?: return@ActionLink
            showPopup(source)
        }

    private fun showPopup(anchor: JComponent) {
        val content = JEditorPane().apply {
            editorKit = HTMLEditorKitBuilder.simple()
            isEditable = false
            border = JBUI.Borders.empty(8, 10)
            text = FlutterWidgetWrapperBundle.message("settings.help.description")
            caretPosition = 0
        }
        val scroll = JBScrollPane(content).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            border = JBUI.Borders.empty()
            preferredSize = JBUI.size(POPUP_SIZE)
        }
        JBPopupFactory.getInstance()
            .createComponentPopupBuilder(scroll, content)
            .setTitle(FlutterWidgetWrapperBundle.message("settings.help.title"))
            .setResizable(true)
            .setMovable(true)
            .setRequestFocus(true)
            .createPopup()
            .show(RelativePoint.getSouthWestOf(anchor))
    }
}
