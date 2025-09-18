package com.jetbrains.bigdatatools.kafka.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.bigdatatools.kafka.core.util.ConnectionUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class MonitoringTabDeleteConnection : MonitoringTabConnectionAction() {
  override fun update(e: AnActionEvent) {
    val selectedConnectionIds = getSelectedConnectionIds(e)
    e.presentation.isEnabled = selectedConnectionIds.isNotEmpty()
    e.presentation.text = KafkaMessagesBundle.message("action.deleteConnection.text", if (selectedConnectionIds.size == 1) 0 else 1)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    if (selectedConnectionIds.isEmpty()) return
    ConnectionUtil.removeConnectionsWithConfirmation(project, selectedConnectionIds)
  }
}