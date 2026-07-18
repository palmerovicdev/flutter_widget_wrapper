package com.palmerodev.fww.detection

/**
 * Name-based widget heuristics used when the Dart Analysis Server has not resolved types.
 * A wrappable widget is an upper-camel identifier that is not one of the common painting,
 * geometry, animation or data types that also read as `Uppercase(` constructor calls.
 */
internal object WidgetNameHeuristics {

    fun isWidgetName(name: String): Boolean =
        name.isNotEmpty() && name[0].isUpperCase() && name !in NON_WIDGET_TYPES

    /**
     * From a reference like `Text`, `ListView.builder`, `Foo&lt;Bar&gt;`, or `prefix.MyWidget`,
     * returns the class name segment used for widget heuristics.
     */
    fun classNameFromReference(referenceText: String): String? {
        val trimmed = referenceText.trim().substringBefore('<').trim()
        if (trimmed.isEmpty()) return null
        return trimmed
            .split('.')
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && it[0].isUpperCase() }
            ?: trimmed.substringBefore('.').trim().takeIf { it.isNotEmpty() }
    }

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
