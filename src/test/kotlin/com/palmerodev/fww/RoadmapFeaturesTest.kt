package com.palmerodev.fww

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.intention.ReplaceWrapperIntention
import com.palmerodev.fww.intention.WrapIntentionRegistrar
import com.palmerodev.fww.intention.WrapWithWidgetIntention
import com.palmerodev.fww.intention.WrapperOptionActions
import com.palmerodev.fww.postfix.WrapperPostfixTemplateProvider
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.Presets
import com.palmerodev.fww.wrappers.ProjectWrappers
import com.palmerodev.fww.wrappers.WrapperRepository
import com.palmerodev.fww.wrappers.WrapperValidator

/**
 * Coverage for the 1.5 roadmap items: Expanded, sliver slots, replace wrapper, project
 * wrappers, hide option, postfix templates, choice tab-stops and presets.
 */
class RoadmapFeaturesTest : BasePlatformTestCase() {

    private fun dart(body: String): String = "void f() {\n$body\n}"

    private val settings get() = FlutterWrapperSettings.getInstance()

    override fun setUp() {
        super.setUp()
        reset()
    }

    override fun tearDown() {
        try {
            reset()
        } finally {
            super.tearDown()
        }
    }

    private fun reset() {
        settings.customWrappersJson = ""
        settings.disabledBuiltInNames = mutableSetOf()
        settings.groupWrappers = false
        settings.caretOnNameOnly = false
    }

    private fun caretAt(marker: String, delta: Int = 1) {
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(marker) + delta)
    }

    private fun available(name: String) =
        WrapWithWidgetIntention(name).isAvailable(project, myFixture.editor, myFixture.file)

    fun `test Expanded is offered only for direct flex children`() {
        myFixture.configureByText("main.dart", dart("Row(children: [Text('a')]);"))
        caretAt("Text")
        assertTrue(available("Expanded"))
        myFixture.configureByText("main.dart", dart("Text('a');"))
        caretAt("Text")
        assertFalse(available("Expanded"))
    }

    fun `test slivers slot offers SliverToBoxAdapter instead of box wrappers`() {
        myFixture.configureByText(
            "main.dart",
            dart("CustomScrollView(slivers: [Text('a'), SliverList(delegate: d)]);"),
        )
        caretAt("Text")
        assertTrue(available("SliverToBoxAdapter"))
        assertFalse(available("SafeArea"))
        assertFalse(available("SliverPadding"))

        caretAt("SliverList")
        assertTrue(available("SliverPadding"))
        assertFalse(available("SliverToBoxAdapter"))
    }

    fun `test replace keeps the child`() {
        myFixture.configureByText("main.dart", dart("Row(children: [Flexible(child: Text('a'))]);"))
        caretAt("Flexible")
        val intention = ReplaceWrapperIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertEquals("Replace Flexible with…", intention.text)

        val target = intention.target(myFixture.editor, myFixture.file)!!
        val expanded = intention.candidates(project, target).first { it.name == "Expanded" }
        intention.replace(project, myFixture.editor, myFixture.file, expanded)
        val result = myFixture.editor.document.text
        assertTrue("Got:\n$result", result.contains("Expanded(") && result.contains("child: Text('a')"))
        assertFalse("Got:\n$result", result.contains("Flexible("))
    }

    fun `test replace is not offered inside the arguments`() {
        myFixture.configureByText("main.dart", dart("Row(children: [Flexible(child: Text('a'))]);"))
        caretAt("child:")
        assertFalse(ReplaceWrapperIntention().isAvailable(project, myFixture.editor, myFixture.file))
    }

    fun `test project file wrappers are available and override personal ones`() {
        myFixture.addFileToProject(
            ProjectWrappers.FILE_NAME,
            """[{"name":"TeamCard","template":["Card(","  child: ${'$'}{widget},",")"]}]""",
        )
        assertEquals(listOf("TeamCard"), ProjectWrappers.load(project).map { it.name })
        WrapIntentionRegistrar.syncRegistrations()

        myFixture.configureByText("main.dart", dart("Text('a');"))
        caretAt("Text")
        assertTrue(available("TeamCard"))
        assertTrue(WrapperRepository.isProjectWrapper("TeamCard", project))
        assertNull("Not visible without the project", WrapperRepository.all().firstOrNull { it.name == "TeamCard" })
    }

    fun `test hide option disables a built-in`() {
        myFixture.configureByText("main.dart", dart("Text('a');"))
        caretAt("Text")
        assertTrue(available("Align"))
        WrapperOptionActions.hide("Align")
        assertFalse(available("Align"))
        assertTrue("Align" in settings.disabledBuiltInNames)
    }

    fun `test wrap intentions expose edit and hide options`() {
        val options = WrapWithWidgetIntention("Align").options.map { it.text }
        assertEquals(listOf("Edit \"Align\" wrapper…", "Hide \"Align\" wrapper"), options)
    }

    fun `test postfix key and target widget`() {
        assertEquals(".singlechildscrollview", WrapperPostfixTemplateProvider.keyOf("SingleChildScrollView"))
        myFixture.configureByText("main.dart", dart("Padding(padding: p, child: Text('a'));"))
        val end = myFixture.file.text.indexOf("Text('a')") + "Text('a')".length
        assertEquals("Text", WrapperPostfixTemplateProvider.widgetEndingAt(myFixture.file, end)?.name)
        val outerEnd = myFixture.file.text.indexOf(");")+1
        assertEquals("Padding", WrapperPostfixTemplateProvider.widgetEndingAt(myFixture.file, outerEnd)?.name)
    }

    fun `test postfix template expands with tab`() {
        myFixture.configureByText("main.dart", dart("Text('a').safearea<caret>;"))
        myFixture.type("\t")
        val result = myFixture.editor.document.text
        assertTrue("Got:\n$result", result.contains("SafeArea(") && result.contains("child: Text('a')"))
        assertFalse("Got:\n$result", result.contains(".safearea"))
    }

    fun `test choice tab-stop starts with the first option`() {
        TemplateManagerImpl.setTemplateTesting(testRootDisposable)
        myFixture.configureByText("main.dart", dart("Text('a');"))
        caretAt("Text")
        WrapWithWidgetIntention("Align").invoke(project, myFixture.editor, myFixture.file)
        val result = myFixture.editor.document.text
        assertTrue("Got:\n$result", result.contains("alignment: Alignment.center"))
        assertFalse("Got:\n$result", result.contains("|"))
    }

    fun `test presets are valid and named uniquely`() {
        assertTrue(Presets.ALL.size >= 20)
        assertTrue(Presets.ALL.all(WrapperValidator::isValid))
        assertEquals(Presets.ALL.size, Presets.ALL.map { it.name }.toSet().size)
    }
}
