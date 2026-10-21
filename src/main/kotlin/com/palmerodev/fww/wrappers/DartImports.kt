package com.palmerodev.fww.wrappers

import com.palmerodev.fww.detection.DartLexer

/**
 * Reads and extends the directive header of a Dart file (`library`, `import`, `export`,
 * `part`) as plain text, so it also works on intention-preview copies and in tests.
 */
object DartImports {

    enum class Kind { LIBRARY, IMPORT, EXPORT, PART, PART_OF }

    /** [start] includes the directive's annotations; [end] is just past its `;`. */
    data class Directive(val kind: Kind, val uri: String?, val prefix: String?, val start: Int, val end: Int)

    data class Insertion(val offset: Int, val text: String)

    private val PREFIX = Regex("""\bas\s+([A-Za-z_$][\w$]*)""")

    fun directives(text: String): List<Directive> {
        val result = mutableListOf<Directive>()
        var i = skipTrivia(text, afterScriptTag(text))
        var annotationStart = -1
        while (i < text.length) {
            if (text[i] == '@') {
                if (annotationStart < 0) annotationStart = i
                i = skipTrivia(text, skipAnnotation(text, i))
                continue
            }
            val word = identifierAt(text, i) ?: break
            val next = skipTrivia(text, i + word.length)
            val kind = when (word) {
                "library" -> Kind.LIBRARY
                "import" -> Kind.IMPORT
                "export" -> Kind.EXPORT
                "part" -> if (identifierAt(text, next) == "of") Kind.PART_OF else Kind.PART
                else -> break
            }
            // `import`/`export`/`part` are followed by their URI; anything else is a declaration.
            if (kind in URI_FIRST && !isQuote(text, next)) break
            val end = directiveEnd(text, next) ?: break
            val uri = firstString(text, next, end)
            val prefix = if (kind == Kind.IMPORT) PREFIX.find(blankStrings(text, next, end))?.groupValues?.get(1) else null
            result += Directive(kind, uri, prefix, if (annotationStart >= 0) annotationStart else i, end)
            annotationStart = -1
            i = skipTrivia(text, end)
        }
        return result
    }

    /** A `part of` file cannot have imports of its own. */
    fun isPart(text: String): Boolean = directives(text).any { it.kind == Kind.PART_OF }

    /** URIs imported without a prefix: the ones whose names are visible as they are. */
    fun importedUris(text: String): Set<String> =
        directives(text).filter { it.kind == Kind.IMPORT && it.prefix == null }.mapNotNullTo(LinkedHashSet()) { it.uri }

    /**
     * Where to add `import '[uri]';` following the `directives_ordering` lint: `dart:`, then
     * `package:`, then relative imports, each group sorted and separated by a blank line.
     * Null for `part of` files.
     */
    fun insertion(text: String, uri: String): Insertion? {
        val all = directives(text)
        if (all.any { it.kind == Kind.PART_OF }) return null
        val line = "import '$uri';"
        val imports = all.filter { it.kind == Kind.IMPORT && it.uri != null }
        val group = group(uri)
        val sameGroup = imports.filter { group(it.uri!!) == group }
        if (sameGroup.isNotEmpty()) {
            val next = sameGroup.firstOrNull { it.uri!! > uri }
            return if (next != null) {
                Insertion(lineStart(text, next.start), "$line\n")
            } else {
                Insertion(lineEnd(text, sameGroup.last().end), "\n$line")
            }
        }
        imports.firstOrNull { group(it.uri!!) > group }?.let {
            return Insertion(lineStart(text, it.start), "$line\n\n")
        }
        imports.lastOrNull { group(it.uri!!) < group }?.let {
            return Insertion(lineEnd(text, it.end), "\n\n$line")
        }
        // No imports yet: after `library`, before exports and parts, or at the top.
        all.firstOrNull { it.kind == Kind.LIBRARY }?.let { return Insertion(lineEnd(text, it.end), "\n\n$line") }
        all.firstOrNull()?.let { return Insertion(lineStart(text, it.start), "$line\n\n") }
        val header = headerEnd(text)
        return if (header != null) Insertion(header, "\n\n$line") else Insertion(afterScriptTag(text), "$line\n\n")
    }

    private val URI_FIRST = setOf(Kind.IMPORT, Kind.EXPORT, Kind.PART)

    private fun group(uri: String): Int = when {
        uri.startsWith("dart:") -> 0
        uri.startsWith("package:") -> 1
        else -> 2
    }

    /**
     * End of a leading non-doc comment block followed by a blank line (a license header),
     * which stays above the imports; null when the file has none.
     */
    private fun headerEnd(text: String): Int? {
        var i = afterScriptTag(text)
        var end: Int? = null
        while (true) {
            while (i < text.length && text[i].isWhitespace()) i++
            if (text.startsWith("///", i) || text.startsWith("/**", i)) break
            i = when {
                text.startsWith("//", i) -> lineEnd(text, i)
                text.startsWith("/*", i) -> skipBlockComment(text, i)
                else -> break
            }
            end = i
        }
        val commentEnd = end ?: return null
        val gap = text.substring(commentEnd, i)
        return commentEnd.takeIf { gap.count { it == '\n' } >= 2 || i == text.length }
    }

    private fun afterScriptTag(text: String): Int =
        if (text.startsWith("#!")) (lineEnd(text, 0) + 1).coerceAtMost(text.length) else 0

    private fun skipTrivia(text: String, from: Int): Int {
        var i = from
        while (i < text.length) {
            i = when {
                text[i].isWhitespace() -> i + 1
                text.startsWith("//", i) -> lineEnd(text, i)
                text.startsWith("/*", i) -> skipBlockComment(text, i)
                else -> return i
            }
        }
        return i
    }

    /** Dart block comments nest. */
    private fun skipBlockComment(text: String, start: Int): Int {
        var depth = 0
        var i = start
        while (i < text.length) {
            when {
                text.startsWith("/*", i) -> { depth++; i += 2 }
                text.startsWith("*/", i) -> { depth--; i += 2; if (depth == 0) return i }
                else -> i++
            }
        }
        return text.length
    }

    /** `@name`, `@a.b` or `@name(...)`, returning the index just past it. */
    private fun skipAnnotation(text: String, at: Int): Int {
        var i = at + 1
        while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_' || text[i] == '$' || text[i] == '.')) i++
        val open = skipTrivia(text, i)
        if (open >= text.length || text[open] != '(') return i
        var depth = 0
        var j = open
        while (j < text.length) {
            when {
                isQuote(text, j) -> { j = DartLexer.skipString(text, j); continue }
                text[j] == '(' -> depth++
                text[j] == ')' -> if (--depth == 0) return j + 1
            }
            j++
        }
        return text.length
    }

    /** Index just past the `;` ending a directive; null when a `{` shows it is not one. */
    private fun directiveEnd(text: String, from: Int): Int? {
        var i = from
        while (i < text.length) {
            when {
                isQuote(text, i) -> { i = DartLexer.skipString(text, i); continue }
                text.startsWith("//", i) || text.startsWith("/*", i) -> { i = skipTrivia(text, i); continue }
                text[i] == ';' -> return i + 1
                text[i] == '{' || text[i] == '}' -> return null
            }
            i++
        }
        return null
    }

    private fun firstString(text: String, from: Int, end: Int): String? {
        var i = from
        while (i < end) {
            if (isQuote(text, i)) {
                val close = DartLexer.skipString(text, i)
                val quote = text[i]
                val triple = text.startsWith("$quote$quote$quote", i)
                val width = if (triple) 3 else 1
                return text.substring(i + width, (close - width).coerceAtLeast(i + width)).trim()
            }
            i++
        }
        return null
    }

    /** The directive text with string contents blanked, so `as` inside a URI is not a prefix. */
    private fun blankStrings(text: String, from: Int, end: Int): String = buildString {
        var i = from
        while (i < end) {
            if (isQuote(text, i)) {
                val close = DartLexer.skipString(text, i).coerceAtMost(end)
                append(" ".repeat(close - i))
                i = close
            } else {
                append(text[i++])
            }
        }
    }

    private fun isQuote(text: String, i: Int): Boolean = i < text.length && (text[i] == '\'' || text[i] == '"')

    private fun identifierAt(text: String, i: Int): String? {
        if (i >= text.length || !(text[i].isLetter() || text[i] == '_' || text[i] == '$')) return null
        var j = i
        while (j < text.length && (text[j].isLetterOrDigit() || text[j] == '_' || text[j] == '$')) j++
        return text.substring(i, j)
    }

    private fun lineStart(text: String, offset: Int): Int = text.lastIndexOf('\n', offset - 1) + 1

    private fun lineEnd(text: String, offset: Int): Int = text.indexOf('\n', offset).let { if (it < 0) text.length else it }
}
