package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper

object WrapperValidator {

    private const val PLACEHOLDER = "\${widget}"

    sealed interface Result {
        data object Ok : Result
        data class Invalid(val reason: String) : Result
    }

    fun validate(wrapper: WidgetWrapper): Result {
        if (wrapper.name.isBlank()) return Result.Invalid("wrapper name is required")
        if (wrapper.template.isEmpty()) return Result.Invalid("template is required")
        val joined = wrapper.template.joinToString("\n")
        if (!joined.contains(PLACEHOLDER)) {
            return Result.Invalid("template must contain $PLACEHOLDER")
        }
        return Result.Ok
    }

    fun isValid(wrapper: WidgetWrapper): Boolean = validate(wrapper) is Result.Ok
}
