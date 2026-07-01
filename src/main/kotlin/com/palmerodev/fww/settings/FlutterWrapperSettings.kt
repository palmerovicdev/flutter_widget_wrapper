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

    override fun getState(): FlutterWrapperSettings = this

    override fun loadState(state: FlutterWrapperSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        fun getInstance(): FlutterWrapperSettings =
            ApplicationManager.getApplication().getService(FlutterWrapperSettings::class.java)

        fun getInstanceOrNull(): FlutterWrapperSettings? =
            ApplicationManager.getApplication()?.getServiceIfCreated(FlutterWrapperSettings::class.java)
    }
}
