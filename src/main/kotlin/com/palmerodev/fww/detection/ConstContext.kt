package com.palmerodev.fww.detection

import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartExpression
import com.jetbrains.lang.dart.psi.DartFile

/**
 * Keeps a wrap from breaking a surrounding `const` expression.
 *
 * Inside `const Column(children: [Text('a')])` every nested constructor is implicitly
 * const, so wrapping `Text` with `GestureDetector(onTap: () {}, ...)` would not compile
 * ("Invalid constant value"). When the wrapper introduces a function literal, the `const`
 * keywords of the enclosing expressions are removed.
 */
object ConstContext {

    /** A closure such as `() {}`, `(x) => x` or `() async {}` can never be const. */
    private val FUNCTION_LITERAL = Regex("""\)\s*(async\s*)?(\{|=>)""")

    fun requiresNonConstContext(renderedWrapper: String): Boolean =
        FUNCTION_LITERAL.containsMatchIn(renderedWrapper)

    /**
     * Ranges of the `const` keywords (plus trailing whitespace) of the expressions that
     * enclose the widget at [widgetRange], outermost last. Empty for non-Dart files.
     */
    fun enclosingConstKeywords(file: PsiFile, widgetRange: TextRange): List<TextRange> {
        if (file !is DartFile) return emptyList()
        var element = file.findElementAt(widgetRange.startOffset) ?: return emptyList()
        while (element.textRange != widgetRange || element !is DartExpression) {
            element = element.parent ?: return emptyList()
            if (element is DartFile) return emptyList()
        }
        val text = file.text
        val result = mutableListOf<TextRange>()
        var current = element.parent
        while (current != null && current !is DartFile) {
            val first = current.firstChild
            if (current is DartExpression && first != null && first.firstChild == null && first.text == "const") {
                var end = first.textRange.endOffset
                while (end < text.length && text[end].isWhitespace()) end++
                result += TextRange(first.textRange.startOffset, end)
            }
            current = current.parent
        }
        return result
    }

    /**
     * Deletes [keywords] (all located before any other pending edit) and returns how many
     * characters were removed, so callers can shift later offsets.
     */
    fun removeKeywords(document: Document, keywords: List<TextRange>): Int {
        var removed = 0
        for (range in keywords.sortedByDescending { it.startOffset }) {
            document.deleteString(range.startOffset, range.endOffset)
            removed += range.length
        }
        return removed
    }
}
