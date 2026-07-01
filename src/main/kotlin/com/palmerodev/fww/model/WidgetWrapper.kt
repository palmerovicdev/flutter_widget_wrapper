package com.palmerodev.fww.model

data class WidgetWrapper(
    val name: String,
    val template: List<String>,
    val description: String? = null,
    val category: String = "Custom",
    val enabled: Boolean = true,
    val allowedParents: List<String> = listOf("any"),
    val disallowedParents: List<String> = emptyList(),
    val requiresDirectParent: Boolean = false,
    val warning: String? = null,
)
