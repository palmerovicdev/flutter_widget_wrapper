package com.palmerodev.fww.detection

object WrappableFieldDetector {

    val WRAPPABLE_FIELDS: Set<String> = setOf("child", "children", "sliver", "slivers", "builder")
    private val LIST_FIELDS: Set<String> = setOf("children", "slivers")
    private val BUILDER_FIELDS: Set<String> = setOf("builder")

    /**
     * [valueStart]..[valueEnd] is the part that becomes `${widget}`: the whole value of a
     * `child:`-like field, or the widget a `builder:` closure returns.
     */
    data class Detected(
        val fieldName: String,
        val valueStart: Int,
        val valueEnd: Int,
        val isList: Boolean,
    )

    /**
     * The first top-level `child`/`children`/`sliver`/`slivers` argument; otherwise the
     * `builder:` closure (e.g. `BlocBuilder`). A `child:` wins over `builder:`, so
     * `AnimatedBuilder(builder: ..., child: X)` keeps templating its child.
     */
    fun find(widgetText: String): Detected? {
        val openParen = widgetText.indexOf('(')
        if (openParen < 0) return null

        var i = openParen + 1
        val n = widgetText.length
        val depth = IntArray(1)
        var builder: Detected? = null

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
                    if (depth[0] == 0) return builder
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
                        val valEnd = findValueEnd(widgetText, j) ?: return builder
                        if (ident !in BUILDER_FIELDS) {
                            return Detected(
                                fieldName = ident,
                                valueStart = valStart,
                                valueEnd = valEnd,
                                isList = ident in LIST_FIELDS,
                            )
                        }
                        if (builder == null) {
                            builder = returnedWidget(widgetText, valStart, valEnd)?.let { (start, end) ->
                                Detected(fieldName = ident, valueStart = start, valueEnd = end, isList = false)
                            }
                        }
                        j = valEnd
                    }
                    i = j
                }
                else -> i++
            }
        }
        return builder
    }

    /**
     * The widget a closure returns: the expression after `=>`, or that of the last `return`
     * directly in a block body. Null for tear-offs (`builder: _build`) and bodies without one.
     */
    private fun returnedWidget(text: String, start: Int, end: Int): Pair<Int, Int>? {
        if (text[start] != '(') return null
        val paramsEnd = skipBalanced(text, start) ?: return null
        val bodyStart = skipWhitespace(text, paramsEnd)
        return when {
            text.startsWith("=>", bodyStart) -> {
                val exprStart = skipWhitespace(text, bodyStart + 2)
                if (exprStart < end) exprStart to end else null
            }
            bodyStart < end && text[bodyStart] == '{' -> lastReturn(text, bodyStart)
            else -> null
        }
    }

    private fun lastReturn(text: String, open: Int): Pair<Int, Int>? {
        val close = (skipBalanced(text, open) ?: return null) - 1
        var last: Pair<Int, Int>? = null
        var depth = 0
        var i = open + 1
        while (i < close) {
            val skipped = skipLiteral(text, i)
            if (skipped != null) {
                i = skipped
                continue
            }
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                'r' if depth == 0 && isKeywordAt(text, i, "return") -> {
                    val exprStart = skipWhitespace(text, i + "return".length)
                    val semicolon = statementEnd(text, exprStart, close) ?: return last
                    val exprEnd = trimTrailingWhitespace(text, exprStart, semicolon)
                    if (exprEnd > exprStart) last = exprStart to exprEnd
                    i = semicolon
                }
            }
            i++
        }
        return last
    }

    /** Index of the `;` ending the statement that starts at [from]. */
    private fun statementEnd(text: String, from: Int, limit: Int): Int? {
        var depth = 0
        var i = from
        while (i < limit) {
            val skipped = skipLiteral(text, i)
            if (skipped != null) {
                i = skipped
                continue
            }
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth--
                ';' if depth == 0 -> return i
            }
            i++
        }
        return null
    }

    /** Index just past the bracket closing the one at [open]. */
    private fun skipBalanced(text: String, open: Int): Int? {
        var depth = 0
        var i = open
        while (i < text.length) {
            val skipped = skipLiteral(text, i)
            if (skipped != null) {
                i = skipped
                continue
            }
            when (text[i]) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> if (--depth == 0) return i + 1
            }
            i++
        }
        return null
    }

    /** Index just past the comment or string starting at [i]; null when there is none. */
    private fun skipLiteral(text: String, i: Int): Int? = when {
        text.startsWith("//", i) -> text.indexOf('\n', i).let { if (it < 0) text.length else it }
        text.startsWith("/*", i) -> text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
        text[i] == '"' || text[i] == '\'' -> skipString(text, i, text[i])
        else -> null
    }

    private fun isKeywordAt(text: String, i: Int, keyword: String): Boolean {
        if (!text.startsWith(keyword, i)) return false
        val before = text.getOrNull(i - 1)
        val after = text.getOrNull(i + keyword.length)
        return (before == null || !(before.isLetterOrDigit() || before == '_')) &&
            (after == null || !(after.isLetterOrDigit() || after == '_'))
    }

    private fun skipWhitespace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i].isWhitespace()) i++
        return i
    }

    private fun findValueEnd(text: String, start: Int): Int? {
        var i = start
        val n = text.length
        var depth = 0
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
                    i = skipString(text, i, c)
                }
                '(', '[', '{' -> {
                    depth++
                    i++
                }
                ')', ']', '}' -> {
                    if (depth == 0) return trimTrailingWhitespace(text, start, i)
                    depth--
                    i++
                }
                ',' if depth == 0 -> return trimTrailingWhitespace(text, start, i)
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
