package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.FlutterWidgetContext
import com.palmerodev.fww.model.WidgetWrapper

object WrapperContextMatcher {

    fun matches(wrapper: WidgetWrapper, ctx: FlutterWidgetContext): Boolean {
        if (!wrapper.enabled) return false
        if (ctx.parentWidgetName == wrapper.name) return false
        if (ctx.parentWidgetName != null && ctx.parentWidgetName in wrapper.disallowedParents) return false
        if (wrapper.requiresDirectParent) {
            val direct = ctx.parentWidgetName ?: return false
            return direct in wrapper.allowedParents
        }
        if ("any" in wrapper.allowedParents) return true
        val chain = buildList {
            ctx.parentWidgetName?.let { add(it) }
            addAll(ctx.ancestors)
        }
        return chain.any { it in wrapper.allowedParents }
    }
}
