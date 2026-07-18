package com.palmerodev.fww

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.settings.WrapperSettingsConfigurable

class WrapperSettingsConfigurableTest : BasePlatformTestCase() {

    private fun freshSettings(): FlutterWrapperSettings = FlutterWrapperSettings.getInstance().apply {
        customWrappersJson = ""
        disabledBuiltInNames = mutableSetOf()
    }

    fun `test component builds for built-ins and custom without error`() {
        freshSettings().customWrappersJson = """
            [{"name":"MyCard","template":["Card(","  child: ${'$'}{widget},",")"],"category":"Custom"}]
        """.trimIndent()

        val configurable = WrapperSettingsConfigurable()
        val component = configurable.createComponent()
        assertNotNull("createComponent must not return null", component)
        assertFalse("A freshly reset panel is not modified", configurable.isModified())
        configurable.disposeUIResources()
    }

    fun `test disabling a built-in round-trips through apply`() {
        freshSettings()
        val configurable = WrapperSettingsConfigurable()
        configurable.createComponent()

        // Simulate what the UI persists when a built-in is unchecked.
        val settings = FlutterWrapperSettings.getInstance()
        settings.disabledBuiltInNames = mutableSetOf("Align")
        configurable.reset()
        assertFalse("Panel matches settings after reset", configurable.isModified())

        settings.disabledBuiltInNames = mutableSetOf()
        assertTrue("Panel differs after settings change out from under it", configurable.isModified())
        configurable.disposeUIResources()
    }
}
