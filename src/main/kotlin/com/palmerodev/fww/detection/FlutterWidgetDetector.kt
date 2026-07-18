package com.palmerodev.fww.detection

import com.intellij.openapi.util.TextRange
import com.palmerodev.fww.model.DetectedWidget

object FlutterWidgetDetector {

    fun detect(fileName: String, text: String, offset: Int): DetectedWidget? {
        if (!fileName.endsWith(".dart")) return null
        val widgets = scanWidgets(text)
        val hit = widgets
            .filter { offset in it.start..it.end }
            .minByOrNull { it.end - it.start }
            ?: return null
        return DetectedWidget(
            name = hit.name,
            range = TextRange(hit.start, hit.end),
            text = text.substring(hit.start, hit.end),
            parentWidgetName = hit.parent,
            ancestors = hit.ancestors,
        )
    }

    private data class ScannedWidget(
        val name: String,
        val start: Int,
        val end: Int,
        val parent: String?,
        val ancestors: List<String>,
    )

    private data class Frame(val name: String?, val nameStart: Int)

    private fun scanWidgets(text: String): List<ScannedWidget> {
        val widgets = mutableListOf<ScannedWidget>()
        val stack = ArrayDeque<Frame>()
        val n = text.length
        var i = 0
        while (i < n) {
            when (val c = text[i]) {
                '/' if i + 1 < n && text[i + 1] == '/' -> {
                    i += 2
                    while (i < n && text[i] != '\n') i++
                }
                '/' if i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                }
                '"', '\'' -> {
                    i = DartLexer.skipString(text, i)
                }
                '(' -> {
                    val (name, nameStart) = DartLexer.lookBackForCallee(text, i)
                    stack.addLast(Frame(name, nameStart ?: i))
                    i++
                }
                ')' -> {
                    val frame = stack.removeLastOrNull()
                    if (frame?.name != null && isWidgetName(frame.name)) {
                        val ancestorNames = stack
                            .asReversed()
                            .mapNotNull { it.name }
                            .filter { isWidgetName(it) }
                        widgets += ScannedWidget(
                            name = frame.name,
                            start = frame.nameStart,
                            end = i + 1,
                            parent = ancestorNames.firstOrNull(),
                            ancestors = ancestorNames,
                        )
                    }
                    i++
                }
                else -> i++
            }
        }
        return widgets
    }

    /**
     * A wrappable widget is an upper-camel identifier that is not one of the common painting,
     * geometry, animation or data types that also read as `Uppercase(` constructor calls but are
     * never Flutter widgets.
     */
    private fun isWidgetName(name: String): Boolean =
        name.isNotEmpty() && name[0].isUpperCase() && name !in NON_WIDGET_TYPES

    private val NON_WIDGET_TYPES = setOf(
        "Duration", "Color", "Colors", "EdgeInsets", "EdgeInsetsDirectional",
        "TextStyle", "StrutStyle", "BoxDecoration", "ShapeDecoration", "BoxConstraints",
        "BorderRadius", "BorderRadiusDirectional", "Border", "BorderSide", "Radius",
        "Offset", "Size", "Rect", "RRect", "Matrix4", "Alignment", "AlignmentDirectional",
        "FractionalOffset", "Curve", "Curves", "Interval", "Cubic", "Tween", "ColorTween",
        "Key", "ValueKey", "GlobalKey", "UniqueKey", "ObjectKey", "PageStorageKey",
        "TextEditingController", "ScrollController", "PageController", "TabController",
        "AnimationController", "FocusNode", "LinearGradient", "RadialGradient",
        "SweepGradient", "Gradient", "BoxShadow", "Shadow", "Paint", "Path", "TextSpan",
        "InlineSpan", "WidgetSpan", "Locale", "Uri", "DateTime", "RegExp", "Random",
        "BigInt", "Rectangle", "InputDecoration", "IconThemeData", "ThemeData",
        "MaterialStateProperty", "WidgetStateProperty", "MediaQueryData",
        "SystemUiOverlayStyle", "OutlineInputBorder", "UnderlineInputBorder",
        "RoundedRectangleBorder", "StadiumBorder", "CircleBorder", "BeveledRectangleBorder",
        "ContinuousRectangleBorder", "VisualDensity", "ButtonStyle", "MaterialColor",
        "HSVColor", "HSLColor", "TextTheme", "IconData", "Icons", "AssetImage",
        "NetworkImage", "MemoryImage", "FileImage", "ImageProvider",
    )
}
