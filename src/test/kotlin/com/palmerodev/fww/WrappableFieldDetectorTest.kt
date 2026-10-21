package com.palmerodev.fww

import com.palmerodev.fww.detection.WrappableFieldDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrappableFieldDetectorTest {

    @Test
    fun `finds top-level child field`() {
        val text = "Padding(padding: EdgeInsets.all(8), child: Text('Hi'))"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("child", found.fieldName)
        assertFalse(found.isList)
        assertEquals("Text('Hi')", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `finds top-level children list field`() {
        val text = "Column(children: [Text('Hi'), Text('Bye')])"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("children", found.fieldName)
        assertTrue(found.isList)
        assertEquals("[Text('Hi'), Text('Bye')]", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `finds sliver field`() {
        val text = "SliverPadding(padding: EdgeInsets.all(8), sliver: SliverList())"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("sliver", found.fieldName)
        assertFalse(found.isList)
    }

    @Test
    fun `ignores nested child in another widget`() {
        val text = "Row(mainAxisAlignment: MainAxisAlignment.center, children: [Container(child: Text('a'))])"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("children", found.fieldName)
    }

    @Test
    fun `returns null when no wrappable field is present`() {
        val text = "Text('Hello', style: TextStyle(color: Colors.red))"
        assertNull(WrappableFieldDetector.find(text))
    }

    @Test
    fun `handles trailing comma in argument list`() {
        val text = """
            Container(
              color: Colors.red,
              child: Text('Hi'),
            )
        """.trimIndent()
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("child", found.fieldName)
        assertEquals("Text('Hi')", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `ignores field inside string literal`() {
        val text = "Text('child: nope', style: TextStyle())"
        assertNull(WrappableFieldDetector.find(text))
    }

    @Test
    fun `handles multiline value with nested brackets`() {
        val text = """
            Padding(
              padding: EdgeInsets.all(8),
              child: Column(
                children: [
                  Text('a'),
                  Text('b'),
                ],
              ),
            )
        """.trimIndent()
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("child", found.fieldName)
        val value = text.substring(found.valueStart, found.valueEnd)
        assertTrue(value.startsWith("Column("))
        assertTrue(value.endsWith(")"))
    }

    @Test
    fun `finds the widget an arrow builder returns`() {
        val text = "BlocBuilder<CounterBloc, int>(builder: (context, count) => Text('hi'))"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("builder", found.fieldName)
        assertFalse(found.isList)
        assertEquals("Text('hi')", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `finds the last top-level return of a block builder`() {
        val text = """
            BlocBuilder<AuthBloc, AuthState>(
              buildWhen: (previous, current) => previous != current,
              builder: (BuildContext context, AuthState state) {
                // return Placeholder();
                final label = () { return 'x'; };
                if (state is AuthLoading) return const CircularProgressIndicator();
                if (state is AuthError) {
                  return Text('error');
                }
                return Column(children: [Text(label())]);
              },
            )
        """.trimIndent()
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("builder", found.fieldName)
        assertEquals("Column(children: [Text(label())])", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `child wins over builder`() {
        val text = "AnimatedBuilder(animation: c, builder: (context, child) => Opacity(opacity: 1, child: child), child: Text('a'))"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("child", found.fieldName)
        assertEquals("Text('a')", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `ternary inside an arrow builder is not read as a child field`() {
        val text = "Builder(builder: (context) => ok ? child : const SizedBox())"
        val found = WrappableFieldDetector.find(text)!!
        assertEquals("builder", found.fieldName)
        assertEquals("ok ? child : const SizedBox()", text.substring(found.valueStart, found.valueEnd))
    }

    @Test
    fun `builder tear-off or body without return is not wrappable`() {
        assertNull(WrappableFieldDetector.find("BlocBuilder<A, B>(builder: _build)"))
        assertNull(WrappableFieldDetector.find("Builder(builder: (context) { if (a) { return X(); } })"))
    }

    @Test
    fun `returns non-null for widget without parentheses only when child present`() {
        assertNotNull(WrappableFieldDetector.find("Center(child: X())"))
        assertNull(WrappableFieldDetector.find("Center"))
    }
}
