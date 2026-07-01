package com.palmerodev.fww.detection

import com.palmerodev.fww.model.DetectedWidget
import com.palmerodev.fww.model.FlutterWidgetContext

object FlutterContextAnalyzer {

    private val FLEX_PARENTS = setOf("Row", "Column", "Flex")

    fun analyze(detected: DetectedWidget): FlutterWidgetContext {
        return FlutterWidgetContext(
            widgetName = detected.name,
            parentWidgetName = detected.parentWidgetName,
            ancestors = detected.ancestors,
            isDirectChildOfFlex = detected.parentWidgetName in FLEX_PARENTS,
            isInsideStack = "Stack" in detected.ancestors,
        )
    }
}
