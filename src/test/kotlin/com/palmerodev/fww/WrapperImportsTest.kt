package com.palmerodev.fww

import com.palmerodev.fww.detection.WidgetLibrary
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.DartImports
import com.palmerodev.fww.wrappers.Presets
import com.palmerodev.fww.wrappers.WrapperImports
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reading and placing import directives, and resolving which ones a wrapper needs. */
class WrapperImportsTest {

    private val widgets = "package:flutter/widgets.dart"
    private val material = "package:flutter/material.dart"
    private val bloc = "package:flutter_bloc/flutter_bloc.dart"
    private val riverpod = "package:flutter_riverpod/flutter_riverpod.dart|package:hooks_riverpod/hooks_riverpod.dart"

    private fun add(text: String, uri: String): String {
        val insertion = DartImports.insertion(text, uri) ?: return text
        return text.substring(0, insertion.offset) + insertion.text + text.substring(insertion.offset)
    }

    private fun wrapper(vararg imports: String) =
        WidgetWrapper(name = "W", template = listOf($$"W(child: ${widget})"), imports = imports.toList())

    // ---- directives ----------------------------------------------------------

    @Test
    fun `reads uris prefixes and conditional imports`() {
        val text = """
            // header
            @TestOn('vm')
            library;

            import 'dart:async'; // trailing
            /* block */
            import 'package:flutter/material.dart' as m show Card;
            import "a.dart" if (dart.library.io) 'b.dart';
            export 'src/c.dart';
            part 'd.g.dart';

            void f() {}
        """.trimIndent()
        val directives = DartImports.directives(text)
        assertEquals(
            listOf(
                DartImports.Kind.LIBRARY, DartImports.Kind.IMPORT, DartImports.Kind.IMPORT,
                DartImports.Kind.IMPORT, DartImports.Kind.EXPORT, DartImports.Kind.PART,
            ),
            directives.map { it.kind },
        )
        assertEquals("the annotation belongs to the library directive", text.indexOf("@TestOn"), directives[0].start)
        assertEquals("m", directives[2].prefix)
        assertEquals(setOf("dart:async", "a.dart"), DartImports.importedUris(text))
    }

    @Test
    fun `a declaration ends the directive header`() {
        val text = "import 'dart:async';\nvoid f() {}\nimport 'x.dart';\n"
        assertEquals(1, DartImports.directives(text).size)
    }

    @Test
    fun `part of files get no imports`() {
        val text = "part of 'main.dart';\n\nvoid f() {}\n"
        assertTrue(DartImports.isPart(text))
        assertNull(DartImports.insertion(text, widgets))
    }

    // ---- insertion -----------------------------------------------------------

    @Test
    fun `inserts in alphabetical order within the package group`() {
        assertEquals(
            "import 'package:flutter/material.dart';\nimport 'package:flutter/widgets.dart';\n\nvoid f() {}\n",
            add("import 'package:flutter/widgets.dart';\n\nvoid f() {}\n", material),
        )
    }

    @Test
    fun `appends after the last import of its group, keeping a trailing comment`() {
        assertEquals(
            "import 'dart:async';\n\nimport 'package:flutter/material.dart'; // ui\nimport '$bloc';\n\nvoid f() {}\n",
            add("import 'dart:async';\n\nimport 'package:flutter/material.dart'; // ui\n\nvoid f() {}\n", bloc),
        )
    }

    @Test
    fun `dart imports go before package imports`() {
        assertEquals(
            "import 'dart:async';\n\nimport 'package:flutter/material.dart';\n\nvoid f() {}\n",
            add("import 'package:flutter/material.dart';\n\nvoid f() {}\n", "dart:async"),
        )
    }

    @Test
    fun `package imports go between dart and relative imports`() {
        assertEquals(
            "import 'dart:async';\n\nimport '$widgets';\n\nvoid f() {}\n",
            add("import 'dart:async';\n\nvoid f() {}\n", widgets),
        )
        assertEquals(
            "import '$widgets';\n\nimport 'src/a.dart';\n\nvoid f() {}\n",
            add("import 'src/a.dart';\n\nvoid f() {}\n", widgets),
        )
    }

    @Test
    fun `without imports goes after library, before parts, or at the top`() {
        assertEquals("library;\n\nimport '$widgets';\n\nvoid f() {}\n", add("library;\n\nvoid f() {}\n", widgets))
        assertEquals(
            "import '$widgets';\n\npart 'a.g.dart';\n\nvoid f() {}\n",
            add("part 'a.g.dart';\n\nvoid f() {}\n", widgets),
        )
        assertEquals("import '$widgets';\n\nvoid f() {}\n", add("void f() {}\n", widgets))
    }

    @Test
    fun `keeps a license header above and a doc comment on its declaration`() {
        assertEquals(
            "// Copyright\n\nimport '$widgets';\n\nvoid f() {}\n",
            add("// Copyright\n\nvoid f() {}\n", widgets),
        )
        assertEquals(
            "import '$widgets';\n\n/// Docs\nclass A {}\n",
            add("/// Docs\nclass A {}\n", widgets),
        )
    }

    // ---- what a wrapper needs ------------------------------------------------

    @Test
    fun `material covers widgets`() {
        assertEquals(emptyList<String>(), WrapperImports.missing(wrapper(widgets), "import '$material';\n", null))
        assertEquals(listOf(widgets), WrapperImports.missing(wrapper(widgets), "void f() {}\n", null))
        assertEquals(listOf(material), WrapperImports.missing(wrapper(material), "import '$widgets';\n", null))
    }

    @Test
    fun `a prefixed import does not cover an unprefixed use`() {
        assertEquals(listOf(material), WrapperImports.missing(wrapper(material), "import '$material' as m;\n", null))
    }

    @Test
    fun `alternatives prefer a package the project depends on`() {
        val hooks = "package:hooks_riverpod/hooks_riverpod.dart"
        val flutterRiverpod = "package:flutter_riverpod/flutter_riverpod.dart"
        assertEquals(listOf(hooks), WrapperImports.missing(wrapper(riverpod), "", setOf("flutter", "hooks_riverpod")))
        assertEquals(listOf(flutterRiverpod), WrapperImports.missing(wrapper(riverpod), "", null))
        assertEquals(emptyList<String>(), WrapperImports.missing(wrapper(riverpod), "import '$hooks';\n", null))
        val none = WrapperImports.missing(wrapper(riverpod), "", setOf("flutter"))
        assertEquals(listOf(flutterRiverpod), none)
        assertEquals(listOf("flutter_riverpod"), WrapperImports.undeclaredPackages(none, setOf("flutter")))
    }

    @Test
    fun `no warning without a pubspec or for dart imports`() {
        assertEquals(emptyList<String>(), WrapperImports.undeclaredPackages(listOf(bloc), null))
        assertEquals(emptyList<String>(), WrapperImports.undeclaredPackages(listOf("dart:async"), setOf("flutter")))
    }

    @Test
    fun `an unchanged copy of a preset inherits its imports`() {
        val consumer = Presets.ALL.first { it.name == "Consumer" }
        val copy = consumer.copy(imports = emptyList())
        assertEquals(consumer.imports, WrapperImports.declared(copy))
        val edited = copy.copy(template = listOf($$"Consumer(builder: (c, r, _) => ${widget})"))
        assertEquals(emptyList<String>(), WrapperImports.declared(edited))
    }

    @Test
    fun `every shipped wrapper declares valid imports`() {
        for (w in BuiltInWrappers.ALL + Presets.ALL) {
            assertFalse("${w.name} declares no imports", w.imports.isEmpty())
            w.imports.flatMap { it.split('|') }.forEach { assertTrue("${w.name}: $it", WrapperImports.isValidUri(it)) }
        }
    }

    @Test
    fun `normalizes pasted directives and rejects non-uris`() {
        assertEquals(bloc, WrapperImports.normalize("import '$bloc';"))
        assertEquals("important.dart", WrapperImports.normalize("important.dart"))
        assertTrue(WrapperImports.isValidUri("dart:async"))
        assertTrue(WrapperImports.isValidUri("src/x.dart"))
        assertFalse(WrapperImports.isValidUri("flutter_bloc"))
    }

    @Test
    fun `imports survive a JSON round trip`() {
        val json = WrapperJsonCodec.encodeList(listOf(wrapper(bloc, riverpod), wrapper()))
        val parsed = WrapperJsonCodec.parseList(json)
        assertEquals(listOf(bloc, riverpod), parsed[0].imports)
        assertEquals(emptyList<String>(), parsed[1].imports)
        assertEquals("only declared imports are written", 1, Regex("\"imports\"").findAll(json).count())
    }

    @Test
    fun `reads package names from pubspec`() {
        val pubspec = """
            name: my_app
            environment:
              sdk: ^3.5.0
            dependencies:
              flutter:
                sdk: flutter
              flutter_bloc: ^8.1.0 # state
              # commented_out: ^1.0.0
            dev_dependencies:
              flutter_test:
                sdk: flutter
            flutter:
              uses-material-design: true
        """.trimIndent()
        assertEquals(
            setOf("my_app", "flutter", "flutter_bloc", "flutter_test"),
            WrapperImports.Pubspec.packages(pubspec),
        )
    }

    // ---- library of a created wrapper ------------------------------------------

    @Test
    fun `maps declaration files to public libraries`() {
        assertEquals(material, WidgetLibrary.publicLibrary("package:flutter/src/material/card.dart", emptySet()))
        assertEquals(widgets, WidgetLibrary.publicLibrary("package:flutter/src/widgets/basic.dart", emptySet()))
        assertEquals(
            bloc,
            WidgetLibrary.publicLibrary("package:flutter_bloc/src/bloc_builder.dart", setOf(material, bloc)),
        )
        assertEquals("package:foo/foo.dart", WidgetLibrary.publicLibrary("package:foo/src/x.dart", emptySet()))
        assertEquals(
            "package:my_app/widgets/my_card.dart",
            WidgetLibrary.publicLibrary("package:my_app/widgets/my_card.dart", emptySet()),
        )
        assertEquals("dart:ui", WidgetLibrary.publicLibrary("dart:ui/painting.dart", emptySet()))
        assertNull(WidgetLibrary.publicLibrary("dart:core/object.dart", emptySet()))
        assertNull(WidgetLibrary.publicLibrary("file:///tmp/x.dart", emptySet()))
    }
}
