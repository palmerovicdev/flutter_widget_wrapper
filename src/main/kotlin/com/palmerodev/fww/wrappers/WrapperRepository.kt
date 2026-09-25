package com.palmerodev.fww.wrappers

import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings

object WrapperRepository {

    /** The merged list for one settings state; rebuilt only when that state changes. */
    private class Snapshot(val disabled: Set<String>, val customJson: String, val wrappers: List<WidgetWrapper>) {
        val byName: Map<String, WidgetWrapper> = wrappers.associateBy { it.name }
    }

    @Volatile
    private var snapshot: Snapshot? = null

    fun all(): List<WidgetWrapper> = current().wrappers

    fun byName(name: String): WidgetWrapper? = current().byName[name]

    private fun current(): Snapshot {
        // getInstance(), not getInstanceOrNull(): the latter hid every custom wrapper
        // whenever the settings service had not been instantiated yet.
        val settings = runCatching { FlutterWrapperSettings.getInstance() }.getOrNull()
        val disabled = settings?.disabledBuiltInNames.orEmpty()
        val customJson = settings?.customWrappersJson.orEmpty()
        snapshot?.takeIf { it.customJson == customJson && it.disabled == disabled }?.let { return it }
        return Snapshot(disabled.toSet(), customJson, merge(disabled, customJson)).also { snapshot = it }
    }

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
