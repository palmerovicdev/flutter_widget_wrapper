package com.palmerodev.fww.intention

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.palmerodev.fww.detection.FlutterContextAnalyzer
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.model.DetectedWidget
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.WrapperContextMatcher
import com.palmerodev.fww.wrappers.WrapperRepository

/** What can be wrapped at the caret, honoring the user's menu preferences. */
internal object WrapTargets {

    private val settings: FlutterWrapperSettings?
        get() = runCatching { FlutterWrapperSettings.getInstance() }.getOrNull()

    /** True when wrappers are offered through a single "Wrap with…" chooser entry. */
    fun grouped(): Boolean = settings?.groupWrappers == true

    fun widgetAt(editor: Editor, file: PsiFile): DetectedWidget? {
        if (!file.name.endsWith(".dart")) return null
        val offset = editor.caretModel.offset
        val detected = FlutterWidgetDetector.detect(file, offset) ?: return null
        if (settings?.caretOnNameOnly == true && !isOnConstructorName(detected, offset)) return null
        return detected
    }

    /** The caret is on `Text` / `ListView.builder` / `const`, not inside the arguments. */
    fun isOnConstructorName(detected: DetectedWidget, offset: Int): Boolean {
        val paren = detected.text.indexOf('(')
        return paren < 0 || offset <= detected.range.startOffset + paren
    }

    fun matches(wrapper: WidgetWrapper, detected: DetectedWidget): Boolean =
        WrapperContextMatcher.matches(wrapper, FlutterContextAnalyzer.analyze(detected))

    fun wrappersFor(detected: DetectedWidget, project: Project): List<WidgetWrapper> {
        val context = FlutterContextAnalyzer.analyze(detected)
        return WrapperRepository.all(project).filter { WrapperContextMatcher.matches(it, context) }
    }
}
