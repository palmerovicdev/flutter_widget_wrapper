package com.palmerodev.fww.wrappers

import com.intellij.openapi.project.Project
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings

/**
 * Built-in, personal (application settings) and project (`.flutter-wrappers.json`) wrappers,
 * merged by name in that order: later sources override earlier ones.
 */
object WrapperRepository {

    /** The merged list for one settings state; rebuilt only when that state changes. */
    private class Snapshot(val disabled: Set<String>, val customJson: String, val wrappers: List<WidgetWrapper>) {
        val byName: Map<String, WidgetWrapper> = wrappers.associateBy { it.name }
    }

    @Volatile
    private var snapshot: Snapshot? = null

    /** Wrappers for [project]; without a project, only built-in and personal ones. */
    fun all(project: Project? = null): List<WidgetWrapper> {
        val base = current().wrappers
        val shared = project?.let(ProjectWrappers::load).orEmpty()
        if (shared.isEmpty()) return base
        val merged = LinkedHashMap<String, WidgetWrapper>()
        for (w in base) merged[w.name] = w
        for (w in shared) merged[w.name] = w
        return merged.values.toList()
    }

    /**
     * Looks [name] up for [project]. Without a project (callers that have no context, like
     * intention priority), open projects are searched after the personal wrappers.
     */
    fun byName(name: String, project: Project? = null): WidgetWrapper? {
        if (project != null) {
            ProjectWrappers.load(project).lastOrNull { it.name == name }?.let { return it }
            return current().byName[name]
        }
        return current().byName[name] ?: ProjectWrappers.fromOpenProjects().lastOrNull { it.name == name }
    }

    /** True when [name] is defined by the project file rather than by the user's settings. */
    fun isProjectWrapper(name: String, project: Project): Boolean =
        ProjectWrappers.load(project).any { it.name == name }

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
