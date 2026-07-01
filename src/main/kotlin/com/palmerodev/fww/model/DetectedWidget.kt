package com.palmerodev.fww.model

import com.intellij.openapi.util.TextRange

data class DetectedWidget(
    val name: String,
    val range: TextRange,
    val text: String,
    val parentWidgetName: String?,
    val ancestors: List<String>,
)
