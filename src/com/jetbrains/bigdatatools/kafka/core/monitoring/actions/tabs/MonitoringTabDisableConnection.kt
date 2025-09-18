package com.jetbrains.bigdatatools.kafka.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.util.ConnectionUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class MonitoringTabDisableConnection : MonitoringTabConnectionAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    val selectedAndEnabledNodes = selectedConnectionIds.filter {
      RfsConnectionDataManager.instance?.getConnectionById(project, it)?.isEnabled == true
    }

    e.presentation.isEnabledAndVisible = selectedAndEnabledNodes.isNotEmpty()
    if (e.presentation.isEnabledAndVisible) {
      e.presentation.text = KafkaMessagesBundle.message("rfs.action.disable.connection", if (selectedAndEnabledNodes.size == 1) 0 else 1)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    if (selectedConnectionIds.isEmpty()) return
    ConnectionUtil.disableConnectionsByIds(project, selectedConnectionIds)
  }
}