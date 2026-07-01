package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.palmerodev.fww.model.WidgetWrapper
import com.palmerodev.fww.settings.FlutterWrapperSettings
import com.palmerodev.fww.wrappers.BuiltInWrappers
import com.palmerodev.fww.wrappers.WrapperJsonCodec
import com.palmerodev.fww.wrappers.WrapperValidator
import java.util.concurrent.atomic.AtomicBoolean

class WrapIntentionRegistrar : ProjectActivity {

    override suspend fun execute(project: Project) {
        if (!REGISTERED.compareAndSet(false, true)) return
        val manager = IntentionManager.getInstance()
        val seen = HashSet<String>()
        for (wrapper in BuiltInWrappers.ALL) {
            registerIfNew(manager, wrapper, seen)
        }
        val settings = FlutterWrapperSettings.getInstanceOrNull() ?: return
        val custom = WrapperJsonCodec.parseList(settings.customWrappersJson)
            .filter(WrapperValidator::isValid)
        for (wrapper in custom) {
            registerIfNew(manager, wrapper, seen)
        }
    }

    private fun registerIfNew(manager: IntentionManager, wrapper: WidgetWrapper, seen: MutableSet<String>) {
        if (!seen.add(wrapper.name)) return
        manager.addAction(WrapWithWidgetIntention(wrapper))
    }

    companion object {
        private val REGISTERED = AtomicBoolean(false)
    }
}
