package com.palmerodev.fww.detection

/**
 * Minimal, PSI-free lexing primitives shared by the widget detectors. Handles the Dart string and
 * comment forms that would otherwise desync a naive paren/bracket scan: single/double/triple
 * quotes, `\` escapes, raw strings (`r'...'`), and `${...}` interpolation whose body is real code.
 */
internal object DartLexer {

    /** True when the quote at [quoteIdx] is preceded by a standalone raw-string prefix (`r`/`R`). */
    fun isRawPrefix(text: String, quoteIdx: Int): Boolean {
        if (quoteIdx == 0) return false
        val p = text[quoteIdx - 1]
        if (p != 'r' && p != 'R') return false
        val beforePrefix = quoteIdx - 2
        return beforePrefix < 0 || !(text[beforePrefix].isLetterOrDigit() || text[beforePrefix] == '_')
    }

    /**
     * Advances past a Dart string literal starting at [start] (the opening quote), returning the
     * index just after it.
     */
    fun skipString(text: String, start: Int, raw: Boolean = isRawPrefix(text, start)): Int {
        val n = text.length
        val quote = text[start]
        val triple = start + 2 < n && text[start + 1] == quote && text[start + 2] == quote
        var i = if (triple) start + 3 else start + 1

        fun atClosing(): Boolean =
            if (triple) i + 2 < n && text[i] == quote && text[i + 1] == quote && text[i + 2] == quote
            else i < n && text[i] == quote

        while (i < n) {
            if (atClosing()) return if (triple) i + 3 else i + 1
            val ch = text[i]
            if (!triple && ch == '\n') return i // unterminated single-line string
            when {
                !raw && ch == '\\' && i + 1 < n -> i += 2
                !raw && ch == '$' && i + 1 < n && text[i + 1] == '{' -> i = skipInterpolation(text, i + 1)
                else -> i++
            }
        }
        return n
    }

    /** Skips a balanced `{...}` interpolation body starting at [braceIdx] (the `{`). */
    fun skipInterpolation(text: String, braceIdx: Int): Int {
        val n = text.length
        var i = braceIdx + 1
        var depth = 1
        while (i < n && depth > 0) {
            when (text[i]) {
                '{' -> { depth++; i++ }
                '}' -> { depth--; i++ }
                '"', '\'' -> i = skipString(text, i)
                else -> i++
            }
        }
        return i
    }

    /**
     * From an opening `(` at [parenIdx], returns the callee identifier and its start offset, or
     * `null to null` when the paren is not a call (e.g. a grouping paren). Skips a trailing generic
     * argument list such as `Foo<Bar>(`.
     */
    fun lookBackForCallee(text: String, parenIdx: Int): Pair<String?, Int?> {
        var i = parenIdx - 1
        while (i >= 0 && text[i].isWhitespace()) i--
        if (i >= 0 && text[i] == '>') {
            var depth = 1
            i--
            while (i >= 0 && depth > 0) {
                when (text[i]) {
                    '>' -> depth++
                    '<' -> depth--
                    ';', '{', '}' -> return null to null
                }
                if (depth > 0) i--
            }
            if (i < 0) return null to null
            i--
            while (i >= 0 && text[i].isWhitespace()) i--
        }
        val end = i + 1
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '_')) i--
        val start = i + 1
        if (start == end) return null to null
        return text.substring(start, end) to start
    }

    /**
     * Splits [text] on top-level commas, ignoring commas nested inside (), [], {}, strings and
     * comments. Trailing empty segments are preserved for the caller to filter.
     */
    fun splitTopLevel(text: String): List<String> {
        val parts = mutableListOf<String>()
        val n = text.length
        var i = 0
        var segStart = 0
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
                '"', '\'' -> i = skipString(text, i)
                '(', '[', '{' -> { depth++; i++ }
                ')', ']', '}' -> { if (depth > 0) depth--; i++ }
                ',' if depth == 0 -> {
                    parts += text.substring(segStart, i)
                    i++
                    segStart = i
                }
                else -> i++
            }
        }
        parts += text.substring(segStart)
        return parts
    }
}
