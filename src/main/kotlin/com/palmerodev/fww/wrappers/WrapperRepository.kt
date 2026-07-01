package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings

object WrapperRepository {

    fun all(): List<WidgetWrapper> {
        val custom = FlutterWrapperSettings.getInstanceOrNull()
            ?.let { WrapperJsonCodec.parseList(it.customWrappersJson) }
            .orEmpty()
            .filter(WrapperValidator::isValid)

        val builtInsByName = BuiltInWrappers.ALL.associateBy { it.name }
        val customByName = custom.associateBy { it.name }
        val merged = LinkedHashMap<String, WidgetWrapper>(builtInsByName)
        for ((name, wrapper) in customByName) {
            merged[name] = wrapper
        }
        return merged.values.toList()
    }
}
