package com.palmerodev.fww

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.detection.WidgetLibrary
import com.palmerodev.fww.intention.WrapApplier
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.intention.WrapWithWidgetIntention
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.WrapperJsonCodec

/** Wrapping adds the imports the wrapper declares; creating a wrapper fills them in. */
class AutoImportTest : BasePlatformTestCase() {

    private val widgets = "import 'package:flutter/widgets.dart';"
    private val material = "import 'package:flutter/material.dart';"

    private fun dart(body: String, header: String = ""): String = "${header}void f() {\n$body\n}"

    override fun tearDown() {
        try {
            FlutterWrapperSettings.getInstance().apply {
                customWrappersJson = ""
                autoImport = true
            }
            WrapIntentionRegistrar.syncRegistrations()
        } finally {
            super.tearDown()
        }
    }

    private fun caretOn(marker: String) {
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(marker) + 1)
    }

    private fun wrap(name: String): String {
        WrapWithWidgetIntention(name).invoke(project, myFixture.editor, myFixture.file)
        return myFixture.editor.document.text
    }

    private fun custom(vararg wrappers: WidgetWrapper) {
        FlutterWrapperSettings.getInstance().customWrappersJson = WrapperJsonCodec.encodeList(wrappers.toList())
        WrapIntentionRegistrar.syncRegistrations()
    }

    private fun blocWrapper(imports: List<String>) = WidgetWrapper(
        name = "MyBlocWrapper",
        template = listOf("BlocProvider(", "  create: (_) => CounterBloc(),", $$"  child: ${widget},", ")"),
        imports = imports,
    )

    fun `test direct wrap adds the missing import at the top`() {
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        caretOn("Text")
        val result = wrap("SafeArea")
        assertTrue("Expected the import first. Got:\n$result", result.startsWith("$widgets\n\nvoid f()"))
        assertTrue("Expected the wrap. Got:\n$result", result.contains("SafeArea("))
    }

    fun `test live template wrap adds the import and keeps the template in place`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        caretOn("Text")
        val result = wrap("Opacity")
        assertTrue("Expected the import first. Got:\n$result", result.startsWith("$widgets\n\nvoid f()"))
        assertTrue("Expected the template after the import. Got:\n$result", result.contains("Opacity("))
        assertTrue("Expected the default value. Got:\n$result", result.contains("opacity: 0.5"))
        assertTrue("The widget must stay inside f. Got:\n$result", result.indexOf("Opacity(") > result.indexOf("void f()"))
    }

    fun `test an existing material import covers widgets`() {
        myFixture.configureByText("main.dart", dart("Text('hi');", header = "$material\n\n"))
        caretOn("Text")
        val result = wrap("SafeArea")
        assertEquals("No import should be added. Got:\n$result", 1, Regex("^import ", RegexOption.MULTILINE).findAll(result).count())
    }

    fun `test InkWell adds material next to widgets`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("main.dart", dart("Text('hi');", header = "$widgets\n\n"))
        caretOn("Text")
        val result = wrap("InkWell")
        assertTrue("Expected material before widgets. Got:\n$result", result.startsWith("$material\n$widgets\n\nvoid f()"))
    }

    fun `test the setting turns auto import off`() {
        FlutterWrapperSettings.getInstance().autoImport = false
        myFixture.configureByText("main.dart", dart("Text('hi');"))
        caretOn("Text")
        val result = wrap("SafeArea")
        assertFalse("No import expected. Got:\n$result", result.contains("import "))
    }

    fun `test a package missing from pubspec is added and reported`() {
        myFixture.addFileToProject("pubspec.yaml", "name: app\ndependencies:\n  flutter:\n    sdk: flutter\n")
        custom(blocWrapper(listOf("package:flutter_bloc/flutter_bloc.dart")))
        myFixture.configureByText("main.dart", dart("Text('hi');", header = "$material\n\n"))
        caretOn("Text")
        val detected = FlutterWidgetDetector.detect(myFixture.file, myFixture.editor.caretModel.offset)!!
        val plan = WrapApplier.plan(myFixture.editor, myFixture.file, blocWrapper(listOf("package:flutter_bloc/flutter_bloc.dart")), detected.range, detected.text)
        assertEquals(listOf("flutter_bloc"), plan.undeclaredPackages)

        val result = wrap("MyBlocWrapper")
        assertTrue(
            "Expected the bloc import after material. Got:\n$result",
            result.startsWith("$material\nimport 'package:flutter_bloc/flutter_bloc.dart';\n\nvoid f()"),
        )
    }

    fun `test alternatives pick the package in pubspec`() {
        myFixture.addFileToProject("pubspec.yaml", "name: app\ndependencies:\n  hooks_riverpod: ^2.0.0\n")
        val spec = "package:flutter_riverpod/flutter_riverpod.dart|package:hooks_riverpod/hooks_riverpod.dart"
        custom(blocWrapper(listOf(spec)))
        myFixture.configureByText("main.dart", dart("Text('hi');", header = "$material\n\n"))
        caretOn("Text")
        val result = wrap("MyBlocWrapper")
        assertTrue("Expected hooks_riverpod. Got:\n$result", result.contains("import 'package:hooks_riverpod/hooks_riverpod.dart';"))
        assertFalse("Expected only one alternative. Got:\n$result", result.contains("flutter_riverpod"))
    }

    fun `test created wrapper takes the import of the shipped wrapper with that name`() {
        myFixture.configureByText("main.dart", dart("Card(child: Text('a'));"))
        caretOn("Card")
        val detected = FlutterWidgetDetector.detect(myFixture.file, myFixture.editor.caretModel.offset)!!
        assertEquals("package:flutter/material.dart", WidgetLibrary.importFor(myFixture.file, detected))
    }

    fun `test created wrapper of an unknown widget has no import`() {
        myFixture.configureByText("main.dart", dart("MyPanel(child: Text('a'));"))
        caretOn("MyPanel")
        val detected = FlutterWidgetDetector.detect(myFixture.file, myFixture.editor.caretModel.offset)!!
        assertNull(WidgetLibrary.importFor(myFixture.file, detected))
    }
}
