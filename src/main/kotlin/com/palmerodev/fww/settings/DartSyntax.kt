package com.palmerodev.fww.settings

import com.intellij.openapi.project.ProjectManager
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.DartLanguage

/** Parses a rendered wrapper preview as a Dart expression to catch syntax errors early. */
internal object DartSyntax {

    private var lastSource: String? = null
    private var lastResult = false

    /** Validation runs on every keystroke; the last answer is reused for unchanged text. */
    fun hasErrors(expression: String): Boolean {
        if (expression == lastSource) return lastResult
        val code = "void f() {\n  $expression;\n}"
        val file = PsiFileFactory.getInstance(ProjectManager.getInstance().defaultProject)
            .createFileFromText("preview.dart", DartLanguage.INSTANCE, code)
        return PsiTreeUtil.hasErrorElements(file).also {
            lastSource = expression
            lastResult = it
        }
    }
}
