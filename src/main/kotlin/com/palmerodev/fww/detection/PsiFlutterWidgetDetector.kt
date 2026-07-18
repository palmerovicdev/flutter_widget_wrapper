package com.palmerodev.fww.detection

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartCallExpression
import com.jetbrains.lang.dart.psi.DartConstObjectExpression
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartNewExpression
import com.palmerodev.fww.model.DetectedWidget

/**
 * Detects the innermost Flutter-like widget constructor under the caret using the Dart plugin PSI.
 */
internal object PsiFlutterWidgetDetector {

    fun detect(file: PsiFile, offset: Int): DetectedWidget? {
        if (file !is DartFile) return null
        val leaf = file.findElementAt(offset) ?: return null

        val chain = mutableListOf<PsiElement>()
        var current: PsiElement? = leaf
        while (current != null && current !is DartFile) {
            if (isWidgetExpression(current)) chain += current
            current = current.parent
        }
        val hit = chain.firstOrNull() ?: return null
        val name = widgetClassName(hit) ?: return null
        val ancestors = chain.drop(1).mapNotNull(::widgetClassName)

        val range = hit.textRange
        return DetectedWidget(
            name = name,
            range = TextRange(range.startOffset, range.endOffset),
            text = hit.text,
            parentWidgetName = ancestors.firstOrNull(),
            ancestors = ancestors,
        )
    }

    fun isWidgetExpression(element: PsiElement): Boolean =
        widgetClassName(element) != null

    fun widgetClassName(element: PsiElement): String? {
        if (element !is DartCallExpression &&
            element !is DartNewExpression &&
            element !is DartConstObjectExpression
        ) {
            return null
        }
        // Prefer text before the argument list so named constructors like `ListView.builder(`
        // resolve to `ListView` even when getExpression() is only the trailing identifier.
        var head = element.text.substringBefore('(').trim()
        head = head.removePrefix("const ").trim()
        head = head.removePrefix("new ").trim()
        if (head.isEmpty()) return null
        val className = WidgetNameHeuristics.classNameFromReference(head) ?: return null
        return className.takeIf { WidgetNameHeuristics.isWidgetName(it) }
    }
}
