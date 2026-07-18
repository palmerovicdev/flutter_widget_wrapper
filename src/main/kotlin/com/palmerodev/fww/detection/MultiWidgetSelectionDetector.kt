package com.palmerodev.fww.detection

/**
 * Detects an editor selection that covers two or more sibling widgets living directly in a
 * `children: [...]` list of a flex parent (Row / Column / Flex). Such a selection can be wrapped as
 * a group — e.g. into a single `Stack`.
 */
object MultiWidgetSelectionDetector {

    private val FLEX_PARENTS = setOf("Row", "Column", "Flex")

    data class Result(
        /** Trimmed source of each selected sibling, in order. */
        val elements: List<String>,
        /** Trimmed selection bounds actually covering the widgets (whitespace/comma excluded). */
        val start: Int,
        val end: Int,
        /** The enclosing flex widget the selection belongs to. */
        val parentWidgetName: String,
    )

    fun analyze(text: String, selectionStart: Int, selectionEnd: Int): Result? {
        var start = selectionStart
        var end = selectionEnd
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        // A trailing comma inside the selection is fine; drop it so the split has no empty tail.
        if (end > start && text[end - 1] == ',') {
            end--
            while (end > start && text[end - 1].isWhitespace()) end--
        }
        if (end - start < 2) return null

        val parent = enclosingFlexParent(text, start) ?: return null

        val elements = DartLexer.splitTopLevel(text.substring(start, end))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (elements.size < 2) return null
        if (!elements.all(::looksLikeWidgetExpression)) return null

        return Result(elements, start, end, parent)
    }

    /**
     * Walks the bracket/paren nesting up to [offset]; returns the flex widget name when the caret
     * sits directly inside a `[...]` list owned by a Row/Column/Flex call, else null.
     */
    private fun enclosingFlexParent(text: String, offset: Int): String? {
        data class Br(val ch: Char, val name: String?)
        val stack = ArrayDeque<Br>()
        var i = 0
        while (i < offset) {
            when (val c = text[i]) {
                '/' if i + 1 < offset && text[i + 1] == '/' -> {
                    i += 2
                    while (i < offset && text[i] != '\n') i++
                }
                '/' if i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2
                    while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i = (i + 2)
                    if (i > offset) return null // selection begins inside a block comment
                }
                '"', '\'' -> {
                    val j = DartLexer.skipString(text, i)
                    if (j > offset) return null // selection begins inside a string
                    i = j
                }
                '(' -> { stack.addLast(Br('(', DartLexer.lookBackForCallee(text, i).first)); i++ }
                '[' -> { stack.addLast(Br('[', null)); i++ }
                '{' -> { stack.addLast(Br('{', null)); i++ }
                ')', ']', '}' -> { stack.removeLastOrNull(); i++ }
                else -> i++
            }
        }
        val entries = stack.toList()
        val listIdx = entries.indexOfLast { it.ch == '[' }
        if (listIdx <= 0) return null
        val owner = entries[listIdx - 1]
        if (owner.ch != '(') return null
        val name = owner.name ?: return null
        return name.takeIf { it in FLEX_PARENTS }
    }

    private fun looksLikeWidgetExpression(element: String): Boolean {
        var e = element.trim()
        // Allow a leading `const` and inline conditionals like `if (cond) Widget()`.
        e = e.removePrefix("const ").trim()
        val first = e.firstOrNull() ?: return false
        return first.isUpperCase() || first == '_' && e.length > 1 && e[1].isUpperCase()
    }
}
