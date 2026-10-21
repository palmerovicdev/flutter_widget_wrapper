package com.palmerodev.fww.detection

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.lang.dart.psi.DartFile
import com.jetbrains.lang.dart.psi.DartReference
import com.jetbrains.lang.dart.util.DartUrlResolver
import com.palmerodev.fww.model.DetectedWidget
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.DartImports
import com.palmerodev.fww.wrappers.Presets
import com.palmerodev.fww.wrappers.WrapperImports

/** Finds the library to import for a widget, e.g. to fill in a wrapper created from it. */
object WidgetLibrary {

    /**
     * The import that provides [widget]'s class, resolved through the Dart plugin; falls back
     * to the shipped wrapper of the same name. Null when the class is declared in [file] or
     * cannot be found.
     */
    fun importFor(file: PsiFile, widget: DetectedWidget): String? {
        val own = file.originalFile.virtualFile
        val target = declarationFile(file, widget)
        if (target != null && own != null) {
            if (target == own) return null
            val url = runCatching { DartUrlResolver.getInstance(file.project, own).getDartUrlForFile(target) }.getOrNull()
            url?.let { publicLibrary(it, DartImports.importedUris(file.text)) }?.let { return it }
        }
        return shippedImport(widget.name)
    }

    /**
     * Turns the URL of the file declaring a class into the library to import. Files under a
     * package's `src/` are private: Flutter's map to the library of their area
     * (`src/material/card.dart` → `material.dart`); other packages use the library this file
     * already imports from that package, or the package's main library.
     */
    internal fun publicLibrary(url: String, imported: Set<String>): String? {
        if (url.startsWith("dart:")) return url.substringBefore('/').takeIf { it != "dart:core" }
        val pkg = WrapperImports.packageOf(url) ?: return null
        val path = url.removePrefix("package:$pkg/")
        if (!path.startsWith("src/")) return url
        if (pkg == "flutter") return "package:flutter/${path.removePrefix("src/").substringBefore('/')}.dart"
        val main = "package:$pkg/$pkg.dart"
        val fromPackage = imported.filter { WrapperImports.packageOf(it) == pkg && !it.startsWith("package:$pkg/src/") }
        return fromPackage.singleOrNull() ?: main.takeIf { it in fromPackage } ?: fromPackage.firstOrNull() ?: main
    }

    private fun declarationFile(file: PsiFile, widget: DetectedWidget): VirtualFile? {
        if (file !is DartFile) return null
        val className = widget.name.substringBefore('.')
        var leaf = file.findElementAt(widget.range.startOffset)
        while (leaf != null && leaf.textRange.startOffset < widget.range.endOffset && leaf.text != className) {
            leaf = PsiTreeUtil.nextLeaf(leaf)
        }
        if (leaf == null || leaf.text != className) return null
        val reference = PsiTreeUtil.getParentOfType(leaf, DartReference::class.java) ?: return null
        val target = runCatching { reference.resolve() }.getOrNull() ?: return null
        return target.containingFile?.originalFile?.virtualFile
    }

    private fun shippedImport(name: String): String? =
        (BuiltInWrappers.ALL + Presets.ALL).firstOrNull { it.name == name }?.imports?.firstOrNull()
}
