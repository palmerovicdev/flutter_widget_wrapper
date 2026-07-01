package com.palmerodev.fww

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.FlutterWidgetWrapperBundle"

internal object FlutterWidgetWrapperBundle {
    private val instance = DynamicBundle(FlutterWidgetWrapperBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any?,
    ): @Nls String = instance.getMessage(key, *params)
}
