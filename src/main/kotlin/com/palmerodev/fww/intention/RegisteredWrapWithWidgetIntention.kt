package com.palmerodev.fww.intention

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInsight.intention.IntentionActionDelegate
/**
 * Gives each dynamically registered wrapper a distinct implementation identity.
 *
 * IntelliJ rejects multiple registered intentions with the same implementation
 * class, even when their text and constructor arguments differ.
 */
internal class RegisteredWrapWithWidgetIntention private constructor(
    private val delegate: WrapWithWidgetIntention,
    private val implementationId: String,
) : IntentionAction by delegate, IntentionActionDelegate {

    constructor(wrapperName: String) : this(
        delegate = WrapWithWidgetIntention(wrapperName),
        implementationId = "${WrapWithWidgetIntention::class.java.name}.$wrapperName",
    )

    override fun getDelegate(): IntentionAction = delegate

    override fun getImplementationClassName(): String = implementationId
}
