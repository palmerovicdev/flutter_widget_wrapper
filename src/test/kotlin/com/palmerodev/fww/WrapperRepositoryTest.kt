package com.palmerodev.fww

import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WrapperRepositoryTest {

    @Test
    fun `merge exposes all built-ins by default`() {
        val merged = WrapperRepository.merge(disabled = emptySet(), customJson = "")
        val builtInNames = BuiltInWrappers.ALL.map { it.name }.toSet()
        assertTrue(merged.map { it.name }.containsAll(builtInNames))
        assertTrue(merged.all { it.enabled })
    }

    @Test
    fun `disabled built-ins come back with enabled=false`() {
        val merged = WrapperRepository.merge(disabled = setOf("InkWell"), customJson = "")
        val inkwell = merged.first { it.name == "InkWell" }
        assertFalse(inkwell.enabled)
        val safeArea = merged.first { it.name == "SafeArea" }
        assertTrue(safeArea.enabled)
    }

    @Test
    fun `custom wrapper with same name overrides built-in`() {
        val json = """
            [
              {
                "name": "SafeArea",
                "template": ["SafeArea(", "  minimum: EdgeInsets.all(8),", "  child: ${'$'}{widget},", ")"],
                "description": "Custom SafeArea"
              }
            ]
        """.trimIndent()
        val merged = WrapperRepository.merge(disabled = emptySet(), customJson = json)
        val safeArea = merged.first { it.name == "SafeArea" }
        assertEquals("Custom SafeArea", safeArea.description)
        assertTrue(safeArea.template.any { it.contains("minimum") })
    }

    @Test
    fun `invalid custom wrapper is filtered out`() {
        val json = """
            [
              { "name": "Broken", "template": "Broken(child: something)" }
            ]
        """.trimIndent()
        val merged = WrapperRepository.merge(disabled = emptySet(), customJson = json)
        assertTrue(merged.none { it.name == "Broken" })
    }

    @Test
    fun `additional custom wrapper is included`() {
        val json = """
            [
              {
                "name": "MyContainer",
                "template": ["Container(", "  child: ${'$'}{widget},", ")"]
              }
            ]
        """.trimIndent()
        val merged = WrapperRepository.merge(disabled = emptySet(), customJson = json)
        assertNotNull(merged.firstOrNull { it.name == "MyContainer" })
    }

    @Test
    fun `blank customJson yields only built-ins`() {
        val merged = WrapperRepository.merge(disabled = emptySet(), customJson = "")
        assertEquals(BuiltInWrappers.ALL.size, merged.size)
    }
}
