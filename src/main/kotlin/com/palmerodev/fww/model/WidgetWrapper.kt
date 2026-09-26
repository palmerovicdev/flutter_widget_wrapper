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
    /**
     * Named arguments the widget must sit in (e.g. `slivers`); empty means any non-sliver
     * slot. Sliver slots (`sliver`, `slivers`) only offer wrappers that list them.
     */
    val allowedSlots: List<String> = emptyList(),
    /** What the wrapped widget must be: [ChildKind.ANY], [ChildKind.BOX] or [ChildKind.SLIVER]. */
    val childKind: String = ChildKind.ANY,
)

object ChildKind {
    const val ANY = "any"
    const val BOX = "box"
    const val SLIVER = "sliver"

    val ALL = listOf(ANY, BOX, SLIVER)
}
