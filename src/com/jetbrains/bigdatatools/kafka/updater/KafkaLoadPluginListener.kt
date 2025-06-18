package com.jetbrains.bigdatatools.kafka.updater

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.notification.Notification.CollapseActionsDirection
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.constants.BdtPlugins
import com.jetbrains.bigdatatools.kafka.core.settings.ConnectionSettings
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.util.executeOnPooledThread
import com.jetbrains.bigdatatools.kafka.core.util.invokeLater
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.toolwindow.KafkaMonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

private val defaultNotificationGroup =
  NotificationGroupManager.getInstance().getNotificationGroup("kafka")

internal class KafkaLoadPluginListener : DynamicPluginListener {
  override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
    if (pluginDescriptor.pluginId.idString != BdtPlugins.KAFKA_ID)
      return

    val notification = defaultNotificationGroup.createNotification(
      KafkaMessagesBundle.message("kafka.plugin.installed"),
      NotificationType.INFORMATION
    )

    notification.collapseDirection = CollapseActionsDirection.KEEP_LEFTMOST
    notification.addAction(
      NotificationAction.create(KafkaMessagesBundle.message("kafka.plugin.try.it")) { e ->
        val project = e.getData(CommonDataKeys.PROJECT) ?: return@create
        ToolWindowManager.getInstance(project).getToolWindow(KafkaMonitoringToolWindowController.TOOL_WINDOW_ID)?.show()
        executeOnPooledThread {
          val kafkaConnections = RfsConnectionDataManager.instance?.getConnections(project)
                                   ?.filter { it.groupId == BdtConnectionType.KAFKA.id } ?: emptyList()
          if (kafkaConnections.isEmpty())
            invokeLater {
              ConnectionSettings.create(project, KafkaConnectionGroup(), KafkaConnectionGroup().createBlankData(), applyIfOk = true)
            }
        }
        notification.expire()
      }
    )

    ProjectManager.getInstance().openProjects.forEach {
      invokeLater {
        if (!it.isDisposed) notification.notify(it)
      }
    }
  }
}