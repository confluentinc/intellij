package com.jetbrains.bigdatatools.kafka.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.jetbrains.bigdatatools.common.settings.ConnectionSettings
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup

class CreateKafkaConnectionAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    ConnectionSettings.create(project, KafkaConnectionGroup(), null, applyIfOk = true)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}