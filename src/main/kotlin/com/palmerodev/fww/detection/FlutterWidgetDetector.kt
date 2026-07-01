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
                c == '(' -> {
                    val (name, nameStart) = lookBackForCallee(text, i)
                    stack.addLast(Frame(name, nameStart ?: i))
                    i++
                }
                c == ')' -> {
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

    private fun lookBackForCallee(text: String, parenIdx: Int): Pair<String?, Int?> {
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
        val name = text.substring(start, end)
        return name to start
    }

    private fun isWidgetName(name: String): Boolean =
        name.isNotEmpty() && name[0].isUpperCase()
}
