package com.palmerodev.fww.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "FlutterWidgetWrapperSettings",
    storages = [Storage("FlutterWidgetWrapper.xml")],
)
class FlutterWrapperSettings : PersistentStateComponent<FlutterWrapperSettings> {

    var customWrappersJson: String = ""
    var disabledBuiltInNames: MutableSet<String> = mutableSetOf()

    /** Offer one "Wrap with…" chooser instead of one Alt+Enter entry per wrapper. */
    var groupWrappers: Boolean = false

    /** Offer wrappers only when the caret is on the constructor name, not in its arguments. */
    var caretOnNameOnly: Boolean = false

    override fun getState(): FlutterWrapperSettings = this

    override fun loadState(state: FlutterWrapperSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): FlutterWrapperSettings =
            ApplicationManager.getApplication().getService(FlutterWrapperSettings::class.java)
    }
}
