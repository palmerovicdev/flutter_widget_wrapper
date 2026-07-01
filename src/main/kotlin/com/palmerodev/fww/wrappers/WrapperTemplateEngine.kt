package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper

object WrapperTemplateEngine {

    private const val PLACEHOLDER = $$"${widget}"

    fun apply(wrapper: WidgetWrapper, widgetText: String, baseIndent: String): String {
        val widgetLines = widgetText.lines()
        val builtLines = mutableListOf<String>()
        for (templateLine in wrapper.template) {
            val placeholderStart = templateLine.indexOf(PLACEHOLDER)
            if (placeholderStart < 0) {
                builtLines += templateLine
                continue
            }
            val before = templateLine.substring(0, placeholderStart)
            val after = templateLine.substring(placeholderStart + PLACEHOLDER.length)
            val lineIndent = before.takeWhile { it == ' ' || it == '\t' }
            if (widgetLines.size == 1) {
                builtLines += before + widgetLines[0] + after
            } else {
                builtLines += before + widgetLines[0]
                for (i in 1 until widgetLines.size - 1) {
                    builtLines += lineIndent + widgetLines[i]
                }
                builtLines += lineIndent + widgetLines.last() + after
            }
        }
        if (baseIndent.isEmpty()) return builtLines.joinToString("\n")
        return builtLines
            .mapIndexed { i, line -> if (i == 0 || line.isEmpty()) line else baseIndent + line }
            .joinToString("\n")
    }
}
