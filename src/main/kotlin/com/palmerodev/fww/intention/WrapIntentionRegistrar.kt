package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import com.palmerodev.fww.wrappers.WrapperValidator

class WrapIntentionRegistrar : ProjectActivity {

    override suspend fun execute(project: Project) {
        syncRegistrations()
    }

    companion object {

        private val registeredNames = mutableSetOf<String>()
        private var registeredCreateWrapper = false
        private val lock = Any()

        fun syncRegistrations() {
            synchronized(lock) {
                val manager = IntentionManager.getInstance()
                if (!registeredCreateWrapper) {
                    manager.addAction(CreateWrapperFromWidgetIntention())
                    registeredCreateWrapper = true
                }
                val settings = FlutterWrapperSettings.getInstanceOrNull()
                val customNames = settings
                    ?.let { WrapperJsonCodec.parseList(it.customWrappersJson) }
                    .orEmpty()
                    .filter(WrapperValidator::isValid)
                    .map { it.name }
                val allNames = LinkedHashSet<String>().apply {
                    BuiltInWrappers.ALL.forEach { add(it.name) }
                    addAll(customNames)
                }
                for (name in allNames) {
                    if (registeredNames.add(name)) {
                        manager.addAction(WrapWithWidgetIntention(name))
                    }
                }
            }
        }
    }
}
