package com.palmerodev.fww.settings

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.EditorTextField
import com.jetbrains.lang.dart.DartFileType

/**
 * Multi-line editor with Dart syntax highlighting for wrapper templates and previews.
 * Templates are not valid Dart (`${widget}`), so it is lexer highlighting only, without
 * a PSI file or error annotations.
 */
internal object DartCodeField {

    fun create(viewer: Boolean): EditorTextField {
        val document = EditorFactory.getInstance().createDocument("")
        val project = ProjectManager.getInstance().defaultProject
        return object : EditorTextField(document, project, DartFileType.INSTANCE, viewer, false) {
            override fun createEditor(): EditorEx = super.createEditor().apply {
                setVerticalScrollbarVisible(true)
                setHorizontalScrollbarVisible(true)
                settings.isLineNumbersShown = !viewer
                settings.isFoldingOutlineShown = false
                settings.additionalLinesCount = 0
            }
        }
    }
}
