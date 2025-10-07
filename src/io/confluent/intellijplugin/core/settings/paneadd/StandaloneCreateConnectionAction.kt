package io.confluent.intellijplugin.core.settings.paneadd

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.settings.ConnectionSettings
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory

class StandaloneCreateConnectionAction(private val project: Project, private val group: ConnectionFactory<*>) :
    DumbAwareAction(group.name, null, group.icon) {

    override fun actionPerformed(e: AnActionEvent) {
        ConnectionSettings.create(project, group, applyIfOk = true)
    }
}