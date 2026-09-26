package com.palmerodev.fww.wrappers

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.palmerodev.fww.model.WidgetWrapper

/**
 * Wrappers shared with a team through VCS: a `.flutter-wrappers.json` file in the project
 * root, in the same format as Settings › Export. They override built-in and personal
 * custom wrappers with the same name, for that project only.
 */
object ProjectWrappers {

    const val FILE_NAME = ".flutter-wrappers.json"

    private class Cached(val stamp: Long, val wrappers: List<WidgetWrapper>)

    private val CACHE = Key.create<Cached>("fww.projectWrappers")

    fun file(project: Project): VirtualFile? =
        if (project.isDisposed) null else project.guessProjectDir()?.findChild(FILE_NAME)

    /** Valid wrappers from the project file; re-read only when the file changes. */
    fun load(project: Project): List<WidgetWrapper> {
        val file = file(project) ?: return emptyList()
        val stamp = file.modificationStamp
        project.getUserData(CACHE)?.takeIf { it.stamp == stamp }?.let { return it.wrappers }
        val text = runCatching { VfsUtilCore.loadText(file) }.getOrDefault("")
        val wrappers = WrapperJsonCodec.parseList(text).filter(WrapperValidator::isValid)
        project.putUserData(CACHE, Cached(stamp, wrappers))
        return wrappers
    }

    /** Project wrappers of every open project, for callers without a project. */
    fun fromOpenProjects(): List<WidgetWrapper> =
        ProjectManager.getInstance().openProjects.flatMap(::load)
}
