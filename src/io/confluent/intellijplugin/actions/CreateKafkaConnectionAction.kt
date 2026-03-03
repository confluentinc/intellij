package io.confluent.intellijplugin.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.intellijplugin.core.settings.ConnectionSettings
import io.confluent.intellijplugin.settings.KafkaConnectionGroup

class CreateKafkaConnectionAction : DumbAwareAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ConnectionSettings.create(project, KafkaConnectionGroup(), null, applyIfOk = true)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}why