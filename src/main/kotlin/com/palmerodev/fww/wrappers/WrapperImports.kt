package com.palmerodev.fww.wrappers

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiFile
import com.palmerodev.fww.model.WidgetWrapper

/**
 * The imports a wrapper needs in a given file. A wrapper declares them in
 * [WidgetWrapper.imports]; each entry is a URI or `a|b` alternatives, any of which is enough.
 */
object WrapperImports {

    /** Libraries that re-export another one: importing a value also imports the key. */
    private val REEXPORTED_BY = mapOf(
        "package:flutter/widgets.dart" to setOf("package:flutter/material.dart", "package:flutter/cupertino.dart"),
    )

    private val DART_URI = Regex("""dart:\w+""")
    private val PACKAGE_URI = Regex("""package:\w+/\S+\.dart""")
    private val RELATIVE_URI = Regex("""[^\s'":]+\.dart""")
    private val IMPORT_KEYWORD = Regex("""^import\s+""")

    fun isValidUri(uri: String): Boolean =
        DART_URI.matches(uri) || PACKAGE_URI.matches(uri) || RELATIVE_URI.matches(uri)

    /** Accepts a pasted directive (`import 'package:a/a.dart';`) as well as a bare URI. */
    fun normalize(raw: String): String =
        raw.trim().replace(IMPORT_KEYWORD, "").trim().removeSuffix(";").trim().trim('\'', '"').trim()

    /** One declared entry with every alternative normalized; blank when it has none. */
    fun normalizeSpec(spec: String): String = spec.split('|').map(::normalize).filter { it.isNotEmpty() }.joinToString("|")

    private fun alternatives(spec: String): List<String> =
        spec.split('|').map(::normalize).filter(::isValidUri)

    /** The pub package an import belongs to; null for `dart:` and relative imports. */
    fun packageOf(uri: String): String? =
        if (uri.startsWith("package:")) uri.removePrefix("package:").substringBefore('/') else null

    /** Shipped wrappers, for custom copies that predate [WidgetWrapper.imports]. */
    private val shipped: Map<String, WidgetWrapper> by lazy {
        (Presets.ALL + BuiltInWrappers.ALL).associateBy { it.name }
    }

    /**
     * The wrapper's own imports. A copy of a shipped wrapper with the same template and none
     * of its own (e.g. a preset added before imports existed) uses the shipped ones.
     */
    fun declared(wrapper: WidgetWrapper): List<String> {
        if (wrapper.imports.isNotEmpty()) return wrapper.imports
        val original = shipped[wrapper.name]?.takeIf { it.template == wrapper.template } ?: return emptyList()
        return original.imports
    }

    fun covered(uri: String, imported: Set<String>): Boolean =
        uri in imported || REEXPORTED_BY[uri].orEmpty().any { it in imported }

    /**
     * The imports to add to [fileText] for [wrapper]. Between alternatives it picks the first
     * whose package is in [dependencies] (null when the project has no pubspec).
     */
    fun missing(wrapper: WidgetWrapper, fileText: String, dependencies: Set<String>?): List<String> {
        val specs = declared(wrapper)
        if (specs.isEmpty() || DartImports.isPart(fileText)) return emptyList()
        val imported = DartImports.importedUris(fileText)
        return specs.mapNotNull { spec ->
            val options = alternatives(spec)
            when {
                options.isEmpty() || options.any { covered(it, imported) } -> null
                dependencies == null -> options.first()
                else -> options.firstOrNull { packageOf(it)?.let(dependencies::contains) ?: true } ?: options.first()
            }
        }.distinct()
    }

    /** Packages of [uris] that are not dependencies of the project. */
    fun undeclaredPackages(uris: List<String>, dependencies: Set<String>?): List<String> {
        if (dependencies == null) return emptyList()
        return uris.mapNotNull(::packageOf).filter { it !in dependencies }.distinct()
    }

    /**
     * Package names the file may import: the dependencies of the nearest `pubspec.yaml`
     * (including dev and overrides) plus the package itself. Null without a pubspec.
     */
    fun dependencies(file: PsiFile): Set<String>? {
        var dir = file.originalFile.virtualFile?.parent
        while (dir != null) {
            val pubspec = dir.findChild("pubspec.yaml")
            if (pubspec != null && !pubspec.isDirectory) {
                val text = FileDocumentManager.getInstance().getCachedDocument(pubspec)?.text
                    ?: runCatching { VfsUtilCore.loadText(pubspec) }.getOrNull()
                    ?: return null
                return Pubspec.packages(text)
            }
            dir = dir.parent
        }
        return null
    }

    /** Just enough YAML to list a pubspec's package names, without a YAML dependency. */
    internal object Pubspec {

        private val SECTIONS = setOf("dependencies", "dev_dependencies", "dependency_overrides")
        private val KEY = Regex("""^(\s*)([A-Za-z_][\w]*)\s*:""")

        fun packages(text: String): Set<String> {
            val result = mutableSetOf<String>()
            var section: String? = null
            var childIndent = -1
            for (raw in text.lines()) {
                val line = raw.substringBefore(" #").takeUnless { it.trimStart().startsWith("#") } ?: continue
                if (line.isBlank()) continue
                val match = KEY.find(line) ?: continue
                val indent = match.groupValues[1].length
                val key = match.groupValues[2]
                if (indent == 0) {
                    section = key.takeIf { it in SECTIONS }
                    childIndent = -1
                    if (key == "name") line.substringAfter(':').trim().trim('\'', '"').takeIf { it.isNotEmpty() }?.let(result::add)
                    continue
                }
                if (section == null) continue
                if (childIndent < 0) childIndent = indent
                if (indent == childIndent) result += key
            }
            return result
        }
    }
}
