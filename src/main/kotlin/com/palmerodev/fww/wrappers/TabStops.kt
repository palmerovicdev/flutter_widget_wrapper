package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper

/**
 * Parsing of live-template markers embedded in wrapper templates.
 *
 * Markers are written by convention inside the template strings, reusing the `${...}`
 * family already used by `${widget}`:
 *
 * - `${widget}`                  the detected widget source (substituted by [WrapperTemplateEngine]).
 * - `${name}` / `${name:default}` an editable tab-stop; `default` is pre-filled and selected.
 * - `${end}`                     the final caret position after all tab-stops.
 *
 * `widget` and `end` are reserved names; any other name is a tab-stop. Marker bodies cannot
 * contain `{` or `}` (so defaults with braces are not supported); the widget source is inserted
 * as literal text elsewhere, so its own `${...}` (Dart interpolation) is never parsed here.
 */
object TabStops {

    const val WIDGET = "widget"
    const val END = "end"

    /** Matches a `${...}` marker whose body has no nested braces. */
    private val MARKER = Regex("\\\$\\{([^{}]*)}")

    sealed interface Token {
        data class Literal(val text: String) : Token
        data object Widget : Token
        data class Variable(val name: String, val default: String) : Token
        data object End : Token
    }

    /** True when the wrapper has at least one non-`widget` marker (a tab-stop or `${end}`). */
    fun hasTabStops(wrapper: WidgetWrapper): Boolean =
        wrapper.template.any { line ->
            MARKER.findAll(line).any { nameOf(it.groupValues[1]) != WIDGET }
        }

    /** Splits an arbitrary (possibly multi-line) string into ordered tokens. */
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var last = 0
        for (match in MARKER.findAll(text)) {
            if (match.range.first > last) {
                tokens += Token.Literal(text.substring(last, match.range.first))
            }
            tokens += classify(match.groupValues[1])
            last = match.range.last + 1
        }
        if (last < text.length) tokens += Token.Literal(text.substring(last))
        return tokens
    }

    /**
     * Renders markers to their default values for display (e.g. the settings preview):
     * `${name:def}` -> `def`, `${name}` -> ``, `${end}` -> ``, leaving `${widget}` intact.
     */
    fun stripToDefaults(text: String): String =
        MARKER.replace(text) { match ->
            val body = match.groupValues[1]
            when (nameOf(body)) {
                WIDGET -> match.value
                END -> ""
                else -> defaultOf(body)
            }
        }

    private fun classify(body: String): Token = when (nameOf(body)) {
        WIDGET -> Token.Widget
        END -> Token.End
        else -> Token.Variable(nameOf(body), defaultOf(body))
    }

    private fun nameOf(body: String): String = body.substringBefore(':').trim()

    private fun defaultOf(body: String): String =
        if (':' in body) body.substringAfter(':') else ""
}
