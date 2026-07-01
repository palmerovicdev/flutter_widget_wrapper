package com.palmerodev.fww.model

data class FlutterWidgetContext(
    val widgetName: String,
    val parentWidgetName: String?,
    val ancestors: List<String>,
    val isDirectChildOfFlex: Boolean,
    val isInsideStack: Boolean,
)
