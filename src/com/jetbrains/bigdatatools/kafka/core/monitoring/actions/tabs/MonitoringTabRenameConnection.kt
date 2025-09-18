package com.jetbrains.bigdatatools.kafka.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.jetbrains.bigdatatools.kafka.core.util.ConnectionUtil

class MonitoringTabRenameConnection : MonitoringTabConnectionAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    if (selectedConnectionIds.isEmpty()) return
    ConnectionUtil.renameConnection(project, selectedConnectionIds.first())
  }
}