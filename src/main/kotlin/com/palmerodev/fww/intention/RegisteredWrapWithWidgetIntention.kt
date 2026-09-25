package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable
import com.intellij.psi.PsiFile
import javax.swing.Icon

/**
 * Gives each dynamically registered wrapper a distinct implementation identity.
 *
 * IntelliJ rejects multiple registered intentions with the same implementation
 * class, even when their text and constructor arguments differ.
 */
internal class RegisteredWrapWithWidgetIntention private constructor(
    private val delegate: WrapWithWidgetIntention,
    private val implementationId: String,
) : IntentionAction by delegate, IntentionActionDelegate, PriorityAction, Iconable {

    constructor(wrapperName: String) : this(
        delegate = WrapWithWidgetIntention(wrapperName),
        implementationId = ID_PREFIX + wrapperName,
    )

    override fun getDelegate(): IntentionAction = delegate

    override fun getImplementationClassName(): String = implementationId

    // Forwarded explicitly: interfaces the delegate implements beyond IntentionAction are
    // not covered by `by delegate`, and the preview is a Java default method.
    override fun getPriority(): PriorityAction.Priority = delegate.priority

    override fun getIcon(flags: Int): Icon = delegate.getIcon(flags)

    override fun generatePreview(project: Project, editor: Editor, file: PsiFile): IntentionPreviewInfo =
        delegate.generatePreview(project, editor, file)

    companion object {
        private val ID_PREFIX = "${WrapWithWidgetIntention::class.java.name}."

        /** The wrapper name behind a registered implementation id, or null for other intentions. */
        fun wrapperNameOf(implementationId: String): String? =
            implementationId.takeIf { it.startsWith(ID_PREFIX) }?.removePrefix(ID_PREFIX)
    }
}
