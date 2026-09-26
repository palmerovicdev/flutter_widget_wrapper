package com.palmerodev.fww.wrappers

import com.palmerodev.fww.detection.DartLexer

/**
 * Structural checks for a wrapper template, beyond [WrapperValidator]'s minimum
 * (name + `${widget}`). Used by the wrapper form to reject templates that would produce
 * broken code; stored wrappers are not filtered by it.
 */
object TemplateLint {

    sealed interface Problem {
        /** A `${` that is never closed, or contains another `{`. */
        data class MalformedMarker(val line: Int) : Problem

        /** `${widget:…}` / `${end:…}`: reserved markers cannot take a default. */
        data class ReservedWithDefault(val name: String) : Problem

        /** A bracket without its counterpart (outside strings and comments). */
        data class Unbalanced(val bracket: Char) : Problem
    }

    private val MARKER = Regex("""\$\{([^{}]*)}""")

    fun check(template: List<String>): List<Problem> {
        val problems = mutableListOf<Problem>()
        template.forEachIndexed { index, line ->
            var at = line.indexOf("\${")
            while (at >= 0) {
                val close = line.indexOf('}', at + 2)
                val nested = line.indexOf('{', at + 2)
                if (close < 0 || (nested in 0 until close)) {
                    problems += Problem.MalformedMarker(index + 1)
                    break
                }
                at = line.indexOf("\${", close + 1)
            }
        }
        val joined = template.joinToString("\n")
        for (match in MARKER.findAll(joined)) {
            val body = match.groupValues[1]
            val name = body.substringBefore(':').trim()
            if (':' in body && (name == TabStops.WIDGET || name == TabStops.END)) {
                problems += Problem.ReservedWithDefault(name)
            }
        }
        // Markers become a plain identifier so the bracket scan sees valid-looking code.
        unbalanced(MARKER.replace(joined, "x"))?.let { problems += Problem.Unbalanced(it) }
        return problems.distinct()
    }

    /** The first unmatched bracket, skipping strings and comments; null when balanced. */
    private fun unbalanced(code: String): Char? {
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
        val stack = ArrayDeque<Char>()
        var i = 0
        while (i < code.length) {
            val c = code[i]
            when {
                c == '/' && i + 1 < code.length && code[i + 1] == '/' -> {
                    while (i < code.length && code[i] != '\n') i++
                }
                c == '/' && i + 1 < code.length && code[i + 1] == '*' -> {
                    val end = code.indexOf("*/", i + 2)
                    i = if (end < 0) code.length else end + 2
                }
                c == '"' || c == '\'' -> i = DartLexer.skipString(code, i)
                c == '(' || c == '[' || c == '{' -> {
                    stack.addLast(c)
                    i++
                }
                c in pairs -> {
                    if (stack.removeLastOrNull() != pairs[c]) return c
                    i++
                }
                else -> i++
            }
        }
        return stack.lastOrNull()
    }
}
