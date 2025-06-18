package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.MonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionFactory
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.KafkaMainController

@Service(Service.Level.PROJECT)
class KafkaMonitoringToolWindowController(project: Project) : MonitoringToolWindowController(project) {
  override val settings
    get() = KafkaToolWindowSettings.getInstance()

  override val helpTopicId: String = "big.data.tools.kafka"

  override val toolWindowId: String = TOOL_WINDOW_ID

  private val settingsListener = KafkaConnectionSettingsListener()

  override fun createConnectionGroup(): ConnectionFactory<*> = KafkaConnectionGroup()

  override fun isSupportedData(connectionData: ConnectionData): Boolean = connectionData is KafkaConnectionData

  override fun createMainController(connectionData: ConnectionData): ComponentController = KafkaMainController(project,
                                                                                                               connectionData as KafkaConnectionData)

  override fun dispose() {
    super.dispose()
    RfsConnectionDataManager.instance?.removeListener(settingsListener)
  }

  override fun setUp(toolWindow: ToolWindow) {
    super.setUp(toolWindow)
    RfsConnectionDataManager.instance?.addListener(settingsListener)
    KafkaRegistryUtil.disableLoggers()
  }

  override fun focusOn(connectionId: String) = focusOn(connectionId, null)

  fun focusOn(connectionId: String, rfsPath: RfsPath?) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

    toolWindow.show {
      val contentManager = toolWindow.contentManager
      val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionId } ?: return@show
      setSelectedContent(content)

      if (rfsPath != null) {
        val mainController = content.getUserData(PAGE_CONTROLLER_ID) as? KafkaMainController ?: return@show
        mainController.open(rfsPath)
      }
    }
  }

  companion object {
    fun getNotificationGroup(): NotificationGroup {
      return NotificationGroupManager.getInstance().getNotificationGroup("Kafka Notification")
    }

    fun getInstance(project: Project): KafkaMonitoringToolWindowController? = project.getService(
      KafkaMonitoringToolWindowController::class.java)

    const val TOOL_WINDOW_ID = "KafkaToolWindow"
  }
}