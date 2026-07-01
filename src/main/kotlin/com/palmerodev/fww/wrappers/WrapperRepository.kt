package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings

object WrapperRepository {

    fun all(): List<WidgetWrapper> {
        val settings = FlutterWrapperSettings.getInstanceOrNull()
        return merge(
            settings?.disabledBuiltInNames.orEmpty(),
            settings?.customWrappersJson.orEmpty(),
        )
    }

    fun byName(name: String): WidgetWrapper? = all().firstOrNull { it.name == name }

    internal fun merge(disabled: Set<String>, customJson: String): List<WidgetWrapper> {
        val custom = WrapperJsonCodec.parseList(customJson).filter(WrapperValidator::isValid)
        val builtIns = BuiltInWrappers.ALL.map { w ->
            if (w.name in disabled) w.copy(enabled = false) else w
        }
        val merged = LinkedHashMap<String, WidgetWrapper>()
        for (w in builtIns) merged[w.name] = w
        for (w in custom) merged[w.name] = w
        return merged.values.toList()
    }
}
