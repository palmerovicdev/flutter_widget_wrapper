package com.palmerodev.fww.intention

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.palmerodev.fww.wrappers.ProjectWrappers

/**
 * Releases what the platform doesn't clean up when this plugin is unloaded without a
 * restart (update or disable): the intentions added at runtime and the per-project cache.
 * Left behind, they keep the old class loader alive, and after an update the registrar
 * would see the old ids and skip registering the new intentions.
 */
class PluginUnloadListener : DynamicPluginListener {
    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString != PLUGIN_ID) return
        WrapIntentionRegistrar.unregisterAll()
        ProjectWrappers.clearCaches()
    }

    private companion object {
        const val PLUGIN_ID = "com.palmerodev.flutter_widget_wrapper"
    }
}
