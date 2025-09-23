package io.confluent.kafka.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import io.confluent.kafka.core.util.ConnectionUtil

abstract class MonitoringTabConnectionAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  protected fun getSelectedConnectionIds(e: AnActionEvent): List<String> {
    val connectionId = e.dataContext.getData(ConnectionUtil.CONNECTION_ID) ?: return emptyList()
    return listOf(connectionId)
  }
}