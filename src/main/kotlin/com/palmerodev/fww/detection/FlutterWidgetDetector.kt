package com.palmerodev.fww.detection

import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile
import com.palmerodev.fww.model.DetectedWidget

object FlutterWidgetDetector {

    /**
     * Uses Dart PSI when [file] is a [DartFile]. Does not fall back to the text scanner on a
     * PSI miss — text detection is only for non-Dart PSI files and the explicit string overload.
     */
    fun detect(file: PsiFile, offset: Int): DetectedWidget? {
        if (file is DartFile) {
            return PsiFlutterWidgetDetector.detect(file, offset)
        }
        return TextFlutterWidgetDetector.detect(file.name, file.text, offset)
    }

    /** Text-only detection (unit tests and callers without a Dart PSI file). */
    fun detect(fileName: String, text: String, offset: Int): DetectedWidget? =
        TextFlutterWidgetDetector.detect(fileName, text, offset)
}
