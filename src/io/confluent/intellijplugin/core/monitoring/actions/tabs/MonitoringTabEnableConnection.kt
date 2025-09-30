package io.confluent.intellijplugin.core.monitoring.actions.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class MonitoringTabEnableConnection : MonitoringTabConnectionAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    val selectedAndDisabledNodes = selectedConnectionIds.filter {
      RfsConnectionDataManager.instance?.getConnectionById(project, it)?.isEnabled == false
    }

    e.presentation.isEnabledAndVisible = selectedAndDisabledNodes.isNotEmpty()
    e.presentation.text = KafkaMessagesBundle.message("rfs.action.enable.connection", if (selectedAndDisabledNodes.size == 1) 0 else 1)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val selectedConnectionIds = getSelectedConnectionIds(e)
    if (selectedConnectionIds.isEmpty()) return
    ConnectionUtil.enableConnectionsByIds(project, selectedConnectionIds)
  }
}