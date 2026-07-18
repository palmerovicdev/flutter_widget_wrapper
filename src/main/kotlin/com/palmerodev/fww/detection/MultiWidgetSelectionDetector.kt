package com.palmerodev.fww.detection

import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartConstObjectExpression
import com.jetbrains.lang.dart.psi.DartElement
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartListLiteralExpression
import com.jetbrains.lang.dart.psi.DartNamedArgument
import com.jetbrains.lang.dart.psi.DartNewExpression

/**
 * Detects an editor selection that covers two or more sibling widgets living directly in a
 * `children: [...]` list of a flex parent (Row / Column / Flex). Such a selection can be wrapped as
 * a group — e.g. into a single `Stack`.
 *
 * Prefers Dart PSI when available; falls back to a text scan otherwise.
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

    fun analyze(file: PsiFile, selectionStart: Int, selectionEnd: Int): Result? {
        if (file is DartFile) {
            analyzePsi(file, selectionStart, selectionEnd)?.let { return it }
        }
        return analyze(file.text, selectionStart, selectionEnd)
    }

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

    private fun analyzePsi(file: DartFile, selectionStart: Int, selectionEnd: Int): Result? {
        val text = file.text
        var start = selectionStart
        var end = selectionEnd
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        if (end > start && text[end - 1] == ',') {
            end--
            while (end > start && text[end - 1].isWhitespace()) end--
        }
        if (end - start < 2) return null

        val leaf = file.findElementAt(start) ?: return null
        val list = PsiTreeUtil.getParentOfType(leaf, DartListLiteralExpression::class.java)
            ?: return null

        val parentName = flexParentName(list) ?: return null

        val selected = list.elementList.filter { el ->
            val r = el.textRange
            r.startOffset >= start && r.endOffset <= end
        }
        if (selected.size < 2) return null
        if (!selected.all(::looksLikeWidgetListElement)) return null

        val selStart = selected.first().textRange.startOffset
        val selEnd = selected.last().textRange.endOffset
        return Result(
            elements = selected.map { it.text.trim() },
            start = selStart,
            end = selEnd,
            parentWidgetName = parentName,
        )
    }

    private fun flexParentName(list: DartListLiteralExpression): String? {
        val named = PsiTreeUtil.getParentOfType(list, DartNamedArgument::class.java) ?: return null
        val paramName = named.parameterReferenceExpression?.text?.trim() ?: return null
        if (paramName != "children") return null
        val owner = PsiTreeUtil.getParentOfType(
            named,
            DartCallExpression::class.java,
            DartNewExpression::class.java,
            DartConstObjectExpression::class.java,
        ) ?: return null
        val name = PsiFlutterWidgetDetector.widgetClassName(owner) ?: return null
        return name.takeIf { it in FLEX_PARENTS }
    }

    private fun looksLikeWidgetListElement(element: DartElement): Boolean {
        if (PsiFlutterWidgetDetector.isWidgetExpression(element)) return true
        val widget = PsiTreeUtil.findChildOfAnyType(
            element,
            true,
            DartCallExpression::class.java,
            DartNewExpression::class.java,
            DartConstObjectExpression::class.java,
        )
        return widget != null && PsiFlutterWidgetDetector.isWidgetExpression(widget)
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
                '(' -> {
                    stack.addLast(Br('(', DartLexer.lookBackForCallee(text, i).first))
                    i++
                }
                '[' -> {
                    stack.addLast(Br('[', null))
                    i++
                }
                '{' -> {
                    stack.addLast(Br('{', null))
                    i++
                }
                ')', ']', '}' -> {
                    stack.removeLastOrNull()
                    i++
                }
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
