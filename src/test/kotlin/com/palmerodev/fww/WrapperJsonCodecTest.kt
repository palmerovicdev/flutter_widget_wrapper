package com.palmerodev.fww

import com.palmerodev.fww.wrappers.WrapperJsonCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WrapperJsonCodecTest {

    @Test
    fun `parses template as string with newlines`() {
        val json = """
            [
              {
                "name": "MyContainer",
                "template": "Container(\n  color: Colors.blue,\n  child: ${'$'}{widget},\n)"
              }
            ]
        """.trimIndent()
        val wrappers = WrapperJsonCodec.parseList(json)
        assertEquals(1, wrappers.size)
        assertEquals("MyContainer", wrappers[0].name)
        assertEquals(4, wrappers[0].template.size)
    }

    @Test
    fun `parses template as array of lines`() {
        val json = """
            [
              {
                "name": "MyExpanded",
                "template": ["Expanded(", "  child: ${'$'}{widget},", ")"],
                "allowedParents": ["Row", "Column", "Flex"],
                "requiresDirectParent": true
              }
            ]
        """.trimIndent()
        val wrappers = WrapperJsonCodec.parseList(json)
        assertEquals(1, wrappers.size)
        val w = wrappers[0]
        assertEquals("MyExpanded", w.name)
        assertEquals(listOf("Row", "Column", "Flex"), w.allowedParents)
        assertTrue(w.requiresDirectParent)
    }

    @Test
    fun `returns empty list for blank input`() {
        assertTrue(WrapperJsonCodec.parseList("").isEmpty())
        assertTrue(WrapperJsonCodec.parseList("   ").isEmpty())
    }

    @Test
    fun `returns empty list for malformed json`() {
        assertTrue(WrapperJsonCodec.parseList("{not valid").isEmpty())
    }
}
