package com.palmerodev.fww.detection

import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile
import com.palmerodev.fww.model.DetectedWidget

object FlutterWidgetDetector {

    /**
     * Prefers Dart PSI when [file] is a parsed [DartFile]; falls back to the text scanner otherwise.
     */
    fun detect(file: PsiFile, offset: Int): DetectedWidget? {
        if (file is DartFile) {
            PsiFlutterWidgetDetector.detect(file, offset)?.let { return it }
        }
        return TextFlutterWidgetDetector.detect(file.name, file.text, offset)
    }

    /** Text-only detection (unit tests and callers without a PSI file). */
    fun detect(fileName: String, text: String, offset: Int): DetectedWidget? =
        TextFlutterWidgetDetector.detect(fileName, text, offset)
}
