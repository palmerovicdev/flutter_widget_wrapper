package com.palmerodev.fww.settings

import com.intellij.ui.ContextHelpLabel
import com.palmerodev.fww.FlutterWidgetWrapperBundle
import javax.swing.JComponent

/** Shared context-help control explaining wrapper template syntax. */
object WrapperSyntaxHelp {

    fun createLabel(): JComponent =
        ContextHelpLabel.create(
            FlutterWidgetWrapperBundle.message("settings.help.title"),
            FlutterWidgetWrapperBundle.message("settings.help.description"),
        )
}
