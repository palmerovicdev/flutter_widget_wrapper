package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.ChildKind
import com.palmerodev.fww.model.WidgetWrapper

object BuiltInWrappers {

    val ALL: List<WidgetWrapper> = listOf(
        WidgetWrapper(
            name = "AnimatedSize",
            template = listOf(
                "AnimatedSize(",
                $$"  duration: const Duration(milliseconds: ${ms:300}),",
                $$"  curve: ${curve:Curves.easeInOut|Curves.linear|Curves.easeIn|Curves.easeOut|Curves.fastOutSlowIn},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with AnimatedSize",
            category = "Animation",
        ),
        WidgetWrapper(
            name = "GestureDetector",
            template = listOf(
                "GestureDetector(",
                $$"  onTap: () {${end}},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with GestureDetector",
            category = "Interaction",
        ),
        WidgetWrapper(
            name = "InkWell",
            template = listOf(
                "InkWell(",
                $$"  onTap: () {${end}},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with InkWell",
            category = "Interaction",
            warning = "InkWell needs a Material ancestor for ripple effects to be visible.",
        ),
        WidgetWrapper(
            name = "Align",
            template = listOf(
                "Align(",
                $$"  alignment: ${alignment:Alignment.center|Alignment.topLeft|Alignment.topCenter|Alignment.topRight|Alignment.centerLeft|Alignment.centerRight|Alignment.bottomLeft|Alignment.bottomCenter|Alignment.bottomRight},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with Align",
            category = "Layout",
        ),
        WidgetWrapper(
            name = "Expanded",
            template = listOf(
                "Expanded(",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with Expanded",
            category = "Layout",
            allowedParents = listOf("Row", "Column", "Flex"),
            requiresDirectParent = true,
        ),
        WidgetWrapper(
            name = "Flexible",
            template = listOf(
                "Flexible(",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with Flexible",
            category = "Layout",
            allowedParents = listOf("Row", "Column", "Flex"),
            requiresDirectParent = true,
        ),
        WidgetWrapper(
            name = "Stack",
            template = listOf(
                "Stack(",
                "  children: [",
                $$"    ${widget},",
                "  ],",
                ")",
            ),
            description = "Wraps with Stack",
            category = "Layout",
        ),
        WidgetWrapper(
            name = "Positioned",
            template = listOf(
                "Positioned(",
                $$"  top: ${top:0},",
                $$"  left: ${left:0},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with Positioned",
            category = "Layout",
            allowedParents = listOf("Stack"),
            requiresDirectParent = true,
        ),
        WidgetWrapper(
            name = "Opacity",
            template = listOf(
                "Opacity(",
                $$"  opacity: ${opacity:0.5},",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with Opacity",
            category = "Visual",
        ),
        WidgetWrapper(
            name = "SingleChildScrollView",
            template = listOf(
                "SingleChildScrollView(",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with SingleChildScrollView",
            category = "Scrolling",
        ),
        WidgetWrapper(
            name = "SafeArea",
            template = listOf(
                "SafeArea(",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Wraps with SafeArea",
            category = "Layout",
        ),
        WidgetWrapper(
            name = "SliverToBoxAdapter",
            template = listOf(
                "SliverToBoxAdapter(",
                $$"  child: ${widget},",
                ")",
            ),
            description = "Puts a box widget into a sliver list",
            category = "Slivers",
            allowedSlots = listOf("sliver", "slivers"),
            childKind = ChildKind.BOX,
        ),
        WidgetWrapper(
            name = "SliverPadding",
            template = listOf(
                "SliverPadding(",
                $$"  padding: const EdgeInsets.all(${padding:8}),",
                $$"  sliver: ${widget},",
                ")",
            ),
            description = "Wraps a sliver with SliverPadding",
            category = "Slivers",
            allowedSlots = listOf("sliver", "slivers"),
            childKind = ChildKind.SLIVER,
        ),
    )
}
