package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper

/** Optional wrappers shipped with the plugin that users can add from Settings. */
object Presets {

    private const val RESOURCE = "/presets/presets.json"

    val ALL: List<WidgetWrapper> by lazy {
        val json = Presets::class.java.getResourceAsStream(RESOURCE)?.bufferedReader()?.use { it.readText() }.orEmpty()
        WrapperJsonCodec.parseList(json).filter(WrapperValidator::isValid)
    }
}
