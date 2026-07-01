package com.palmerodev.fww.detection

object WrappableFieldDetector {

    val WRAPPABLE_FIELDS: Set<String> = setOf("child", "children", "sliver", "slivers")
    private val LIST_FIELDS: Set<String> = setOf("children", "slivers")

    data class Detected(
        val fieldName: String,
        val valueStart: Int,
        val valueEnd: Int,
        val isList: Boolean,
    )

    fun find(widgetText: String): Detected? {
        val openParen = widgetText.indexOf('(')
        if (openParen < 0) return null

        var i = openParen + 1
        val n = widgetText.length
        val depth = IntArray(1)

        while (i < n) {
            val c = widgetText[i]
            when {
                c == '/' && i + 1 < n && widgetText[i + 1] == '/' -> {
                    i += 2
                    while (i < n && widgetText[i] != '\n') i++
                }
                c == '/' && i + 1 < n && widgetText[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(widgetText[i] == '*' && widgetText[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                }
                c == '"' || c == '\'' -> {
                    i = skipString(widgetText, i, c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth[0]++
                    i++
                }
                c == ')' || c == ']' || c == '}' -> {
                    if (depth[0] == 0) return null
                    depth[0]--
                    i++
                }
                depth[0] == 0 && (c.isLetter() || c == '_') -> {
                    val idStart = i
                    while (i < n && (widgetText[i].isLetterOrDigit() || widgetText[i] == '_')) i++
                    val ident = widgetText.substring(idStart, i)
                    var j = i
                    while (j < n && widgetText[j].isWhitespace()) j++
                    if (j < n && widgetText[j] == ':' && ident in WRAPPABLE_FIELDS) {
                        j++
                        while (j < n && widgetText[j].isWhitespace()) j++
                        val valStart = j
                        val valEnd = findValueEnd(widgetText, j) ?: return null
                        return Detected(
                            fieldName = ident,
                            valueStart = valStart,
                            valueEnd = valEnd,
                            isList = ident in LIST_FIELDS,
                        )
                    }
                    i = j
                }
                else -> i++
            }
        }
        return null
    }

    private fun findValueEnd(text: String, start: Int): Int? {
        var i = start
        val n = text.length
        var depth = 0
        while (i < n) {
            val c = text[i]
            when {
                c == '/' && i + 1 < n && text[i + 1] == '/' -> {
                    i += 2
                    while (i < n && text[i] != '\n') i++
                }
                c == '/' && i + 1 < n && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < n && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2).coerceAtMost(n)
                }
                c == '"' || c == '\'' -> {
                    i = skipString(text, i, c)
                }
                c == '(' || c == '[' || c == '{' -> {
                    depth++
                    i++
                }
                c == ')' || c == ']' || c == '}' -> {
                    if (depth == 0) return trimTrailingWhitespace(text, start, i)
                    depth--
                    i++
                }
                c == ',' && depth == 0 -> return trimTrailingWhitespace(text, start, i)
                else -> i++
            }
        }
        return null
    }

    private fun trimTrailingWhitespace(text: String, start: Int, end: Int): Int {
        var e = end
        while (e > start && text[e - 1].isWhitespace()) e--
        return e
    }

    private fun skipString(text: String, start: Int, quote: Char): Int {
        val n = text.length
        val triple = start + 2 < n && text[start + 1] == quote && text[start + 2] == quote
        var i = if (triple) start + 3 else start + 1
        if (triple) {
            while (i + 2 < n && !(text[i] == quote && text[i + 1] == quote && text[i + 2] == quote)) {
                if (text[i] == '\\' && i + 1 < n) i++
                i++
            }
            return (i + 3).coerceAtMost(n)
        }
        while (i < n && text[i] != quote && text[i] != '\n') {
            if (text[i] == '\\' && i + 1 < n) i++
            i++
        }
        return (i + 1).coerceAtMost(n)
    }
}
