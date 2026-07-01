package com.palmerodev.fww

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.wrappers.WrapperValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WrapperValidatorTest {

    @Test
    fun `template without placeholder is invalid`() {
        val wrapper = WidgetWrapper(
            name = "Bad",
            template = listOf("Container(child: something)"),
        )
        val result = WrapperValidator.validate(wrapper)
        assertTrue(result is WrapperValidator.Result.Invalid)
        assertFalse(WrapperValidator.isValid(wrapper))
    }

    @Test
    fun `blank name is invalid`() {
        val wrapper = WidgetWrapper(
            name = "  ",
            template = listOf("Foo(\${widget})"),
        )
        assertFalse(WrapperValidator.isValid(wrapper))
    }

    @Test
    fun `well formed wrapper is valid`() {
        val wrapper = WidgetWrapper(
            name = "SafeArea",
            template = listOf("SafeArea(child: \${widget})"),
        )
        assertTrue(WrapperValidator.isValid(wrapper))
    }
}
