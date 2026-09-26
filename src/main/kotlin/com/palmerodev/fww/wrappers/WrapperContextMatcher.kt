package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.ChildKind
import com.palmerodev.fww.model.FlutterWidgetContext
import com.palmerodev.fww.model.WidgetWrapper

object WrapperContextMatcher {

    /** Slots whose value must be a sliver; box wrappers are never valid there. */
    val SLIVER_SLOTS = setOf("sliver", "slivers")

    /** Name-based: `SliverList`, `SliverAppBar`, … (types are not resolved). */
    fun isSliverName(name: String): Boolean = name.startsWith("Sliver")

    fun matches(wrapper: WidgetWrapper, ctx: FlutterWidgetContext): Boolean {
        if (!wrapper.enabled) return false
        if (!matchesSlot(wrapper, ctx.slot)) return false
        when (wrapper.childKind) {
            ChildKind.BOX -> if (isSliverName(ctx.widgetName)) return false
            ChildKind.SLIVER -> if (!isSliverName(ctx.widgetName)) return false
        }
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

    private fun matchesSlot(wrapper: WidgetWrapper, slot: String?): Boolean =
        if (wrapper.allowedSlots.isEmpty()) {
            slot !in SLIVER_SLOTS
        } else {
            slot != null && slot in wrapper.allowedSlots
        }
}
