package com.palmerodev.fww.detection

import com.intellij.psi.PsiFile
import com.jetbrains.lang.dart.psi.DartFile
import com.palmerodev.fww.model.DetectedWidget
import java.lang.ref.WeakReference

object FlutterWidgetDetector {

    /**
     * Every registered wrap intention calls [detect] from `isAvailable` for the same caret,
     * so the last result is reused while the file, its PSI stamp and the offset are unchanged.
     */
    private class Entry(file: PsiFile, val stamp: Long, val offset: Int, val result: DetectedWidget?) {
        private val fileRef = WeakReference(file)
        fun matches(file: PsiFile, stamp: Long, offset: Int): Boolean =
            fileRef.get() === file && this.stamp == stamp && this.offset == offset
    }

    @Volatile
    private var last: Entry? = null

    /**
     * Uses Dart PSI when [file] is a [DartFile]. Does not fall back to the text scanner on a
     * PSI miss — text detection is only for non-Dart PSI files and the explicit string overload.
     */
    fun detect(file: PsiFile, offset: Int): DetectedWidget? {
        val stamp = file.modificationStamp
        last?.takeIf { it.matches(file, stamp, offset) }?.let { return it.result }
        val result = if (file is DartFile) {
            PsiFlutterWidgetDetector.detect(file, offset)
        } else {
            TextFlutterWidgetDetector.detect(file.name, file.text, offset)
        }
        last = Entry(file, stamp, offset, result)
        return result
    }

    /** Text-only detection (unit tests and callers without a Dart PSI file). */
    fun detect(fileName: String, text: String, offset: Int): DetectedWidget? =
        TextFlutterWidgetDetector.detect(fileName, text, offset)
}
