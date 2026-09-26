package com.palmerodev.fww.intention

import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.palmerodev.fww.wrappers.ProjectWrappers

/** Re-registers wrap intentions when a project's `.flutter-wrappers.json` changes. */
class ProjectWrappersListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        if (events.any { it.path.endsWith("/${ProjectWrappers.FILE_NAME}") }) {
            WrapIntentionRegistrar.syncRegistrations()
        }
    }
}
