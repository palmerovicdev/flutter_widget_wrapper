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
     * True when [member] may be a Flutter widget named constructor / factory
     * (`ListView.builder`, `Image.asset`) rather than a non-widget static
     * (`Theme.of`, `List.generate`).
     */
    fun isPromotableNamedMember(member: String): Boolean =
        member.isNotEmpty() && member[0].isLowerCase() && member !in NON_WIDGET_STATIC_MEMBERS

    /**
     * From a reference like `Text`, `ListView.builder`, `Foo&lt;Bar&gt;`, or `prefix.MyWidget`,
     * returns the class name segment used for widget heuristics, or null when the call looks
     * like a non-widget static (`Theme.of`).
     */
    fun classNameFromReference(referenceText: String): String? {
        val trimmed = referenceText.trim().substringBefore('<').trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        if (parts.size == 1) {
            return parts[0].takeIf { it[0].isUpperCase() }
        }
        val classIdx = parts.indexOfFirst { it[0].isUpperCase() }
        if (classIdx < 0) return null
        val className = parts[classIdx]
        val rest = parts.drop(classIdx + 1)
        return when {
            rest.isEmpty() -> className
            rest.size == 1 && rest[0][0].isLowerCase() ->
                className.takeIf { isPromotableNamedMember(rest[0]) }
            else -> null
        }
    }

    /** Static / factory members that must not be promoted to a wrappable class name. */
    private val NON_WIDGET_STATIC_MEMBERS = setOf(
        "of", "maybeOf", "from", "fromMap", "fromJson", "fromList", "fromEntries",
        "generate", "delayed", "sync", "microtask", "error", "parse", "tryParse",
        "lerp", "lerpDouble", "all", "only", "symmetric", "zero", "infinite",
        "circular", "vertical", "horizontal", "fromLTRB", "fromSTEB", "copyWith",
    )

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
        "List", "Map", "Set", "Future", "Completer", "Stream", "Iterable", "Iterator",
        "ValueNotifier", "ChangeNotifier", "FlutterError", "Error", "Exception",
        "State", "Object", "Type", "Symbol", "StackTrace", "Num", "Int", "Double", "Bool",
        "String", "Rune", "Pattern", "Match", "Comparable",
    )
}
