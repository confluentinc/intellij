package com.jetbrains.bigdatatools.kafka.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.bigdatatools.kafka.core.settings.ConnectionSettings

class MonitoringTabOpenConnectionSetting : MonitoringTabConnectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    if (selectedConnectionIds.isEmpty()) return
    ConnectionSettings.open(project, selectedConnectionIds.first())
  }
}


