package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.IntentionManager
import com.intellij.openapi.diagnostic.logger
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

        private val LOG = logger<WrapIntentionRegistrar>()
        private val lock = Any()

        /**
         * Registers one intention per wrapper, plus the singleton intentions, and
         * unregisters those whose wrapper no longer exists.
         *
         * Idempotent and self-healing: what is already registered is derived from
         * [IntentionManager] itself rather than from a local bookkeeping set, so a
         * startup run that was cancelled or failed midway is fully repaired by the
         * next call (project opened, settings applied).
         */
        fun syncRegistrations() {
            synchronized(lock) {
                runCatching { doSync() }
                    .onFailure { LOG.warn("Could not register Flutter wrap intentions", it) }
            }
        }

        private fun doSync() {
            val manager = IntentionManager.getInstance()
            val names = wrapperNames()

            // Drop intentions of custom wrappers that were renamed or deleted.
            for (action in manager.intentionActions) {
                val name = RegisteredWrapWithWidgetIntention.wrapperNameOf(implementationIdOf(action)) ?: continue
                if (name !in names) manager.unregisterIntention(action)
            }

            val registered = manager.intentionActions.mapTo(mutableSetOf()) { implementationIdOf(it) }

            if (CreateWrapperFromWidgetIntention::class.java.name !in registered) {
                manager.addAction(CreateWrapperFromWidgetIntention())
            }
            if (WrapSelectionIntention::class.java.name !in registered) {
                manager.addAction(WrapSelectionIntention())
            }
            if (WrapWithChooserIntention::class.java.name !in registered) {
                manager.addAction(WrapWithChooserIntention())
            }

            for (name in names) {
                val action = RegisteredWrapWithWidgetIntention(name)
                if (action.getImplementationClassName() !in registered) {
                    manager.addAction(action)
                }
            }
        }

        /** Built-in names first, then valid custom ones, de-duplicated. */
        private fun wrapperNames(): Set<String> {
            // getInstance(), not getInstanceOrNull(): during startup registration the
            // service has usually not been touched yet, and skipping it here silently
            // dropped every custom wrapper until the user re-saved the settings.
            val customNames = runCatching { FlutterWrapperSettings.getInstance() }
                .getOrNull()
                ?.let { WrapperJsonCodec.parseList(it.customWrappersJson) }
                .orEmpty()
                .filter(WrapperValidator::isValid)
                .map { it.name }
            return LinkedHashSet<String>().apply {
                BuiltInWrappers.ALL.forEach { add(it.name) }
                addAll(customNames)
            }
        }

        private fun implementationIdOf(action: IntentionAction): String =
            (action as? IntentionActionDelegate)?.implementationClassName ?: action.javaClass.name
    }
}
