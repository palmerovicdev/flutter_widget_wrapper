package com.palmerodev.fww

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.detection.FlutterWidgetDetector
import com.palmerodev.fww.intention.CreateWrapperFromWidgetIntention
import com.palmerodev.fww.intention.WrapWithWidgetIntention
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.WrapperJsonCodec

/** "Create wrapper from …" on widgets whose content comes from a `builder:` closure. */
class CreateWrapperFromWidgetTest : BasePlatformTestCase() {

    private fun dart(body: String): String = "void f() {\n$body\n}"

    override fun tearDown() {
        try {
            FlutterWrapperSettings.getInstance().customWrappersJson = ""
        } finally {
            super.tearDown()
        }
    }

    private fun caretOn(marker: String) {
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf(marker) + 1)
    }

    private val blocBuilder = """
        BlocBuilder<CounterBloc, int>(
          builder: (context, count) {
            if (count < 0) return const SizedBox();
            return Text('hi');
          },
        );
    """.trimIndent()

    fun `test create wrapper is offered on a BlocBuilder`() {
        myFixture.configureByText("main.dart", dart(blocBuilder))
        caretOn("BlocBuilder")
        val intention = CreateWrapperFromWidgetIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, myFixture.file))
        assertEquals("Create wrapper from BlocBuilder", intention.text)
    }

    fun `test builder template keeps the closure and its early returns`() {
        myFixture.configureByText("main.dart", dart(blocBuilder))
        caretOn("BlocBuilder")
        val detected = FlutterWidgetDetector.detect(myFixture.file, myFixture.editor.caretModel.offset)!!
        val template = CreateWrapperFromWidgetIntention().templateFor(detected.text)
        assertEquals(
            $$"""
            BlocBuilder<CounterBloc, int>(
              builder: (context, count) {
                if (count < 0) return const SizedBox();
                return ${widget};
              },
            )
            """.trimIndent(),
            template,
        )
    }

    fun `test wrapper created from a builder wraps another widget`() {
        myFixture.configureByText("main.dart", dart(blocBuilder))
        caretOn("BlocBuilder")
        val detected = FlutterWidgetDetector.detect(myFixture.file, myFixture.editor.caretModel.offset)!!
        val template = CreateWrapperFromWidgetIntention().templateFor(detected.text)!!
        FlutterWrapperSettings.getInstance().customWrappersJson = WrapperJsonCodec.encodeList(
            listOf(WidgetWrapper(name = "CounterBuilder", template = template.split('\n'))),
        )

        myFixture.configureByText("main.dart", dart("Icon(Icons.add);"))
        caretOn("Icon")
        WrapWithWidgetIntention("CounterBuilder").invoke(project, myFixture.editor, myFixture.file)
        val result = myFixture.editor.document.text
        assertTrue("Got:\n$result", result.contains("BlocBuilder<CounterBloc, int>("))
        assertTrue("Got:\n$result", result.contains("return Icon(Icons.add);"))
    }
}
