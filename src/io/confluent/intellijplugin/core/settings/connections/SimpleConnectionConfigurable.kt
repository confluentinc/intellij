package io.confluent.intellijplugin.core.settings.connections

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import javax.swing.Icon

class SimpleConnectionConfigurable<D : ConnectionData>(
    connectionData: D,
    project: Project,
    iconUnexpanded: Icon? = null,
    iconExpanded: Icon? = iconUnexpanded
) : ConnectionConfigurable<D, SettingsPanelCustomizer<D>>(connectionData, project, iconUnexpanded, iconExpanded) {
    override fun createSettingsCustomizer() = object : SettingsPanelCustomizer<D>() {
        override fun getDefaultFields(): List<WrappedComponent<in D>> = emptyList()
    }
}