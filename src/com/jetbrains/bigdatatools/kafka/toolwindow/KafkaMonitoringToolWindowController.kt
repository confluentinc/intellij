package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.ClusterPageController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.toolwindow.ComponentController
import com.jetbrains.bigdatatools.monitoring.toolwindow.MonitoringToolWindowController
import com.jetbrains.bigdatatools.settings.ConnectionSettings
import com.jetbrains.bigdatatools.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.ui.AutorefreshPopupComponent
import com.jetbrains.bigdatatools.ui.CustomComponentActionImpl
import javax.swing.BorderFactory

class KafkaMonitoringToolWindowController(project: Project) : MonitoringToolWindowController(project) {

  override val helpTopicId: String
    get() = "big.data.tools.kafka"

  override fun createConnectionGroup(): ConnectionGroup = KafkaConnectionGroup()

  override fun isSupportedData(connectionData: ConnectionData): Boolean = connectionData is KafkaConnectionData

  override fun createMainController(connectionData: ConnectionData): ComponentController = ClusterPageController(project,
                                                                                                                 connectionData as KafkaConnectionData)

  fun setUp(toolWindow: ToolWindow) {
    contentManager = toolWindow.contentManager
    contentManager.addDataProvider(helpIdProvider)

    val autorefresh = AutorefreshPopupComponent()
    autorefresh.isOpaque = false
    autorefresh.value = KafkaToolWindowSettings.getInstance().dataUpdateIntervalMillis
    autorefresh.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)

    autorefresh.onRefreshIntervalChanged = { value ->
      onRefreshIntervalChanged(value)
    }

    autorefresh.onActionPerformed = {
      onRefreshAction()
    }

    val actionsGroup = DefaultActionGroup()
    actionsGroup.add(object : DumbAwareAction(KafkaMessagesBundle.message("open.settings.action.text"),
                                              KafkaMessagesBundle.message("open.settings.action.description"), AllIcons.General.Settings) {
      override fun displayTextInToolbar(): Boolean = true

      override fun actionPerformed(e: AnActionEvent) {
        ConnectionSettings.open(project, contentManager.selectedContent?.getUserData(CONNECTION_ID))
      }
    })
    actionsGroup.addSeparator()
    actionsGroup.add(CustomComponentActionImpl(autorefresh))

    (toolWindow as? ToolWindowEx)?.setTitleActions(listOf(actionsGroup))

    val connectionSettingsManager = RfsConnectionDataManager.instance
    val connections = connectionSettingsManager?.getTyped<KafkaConnectionData>(project) ?: emptyList()
    connectionSettingsManager?.addListener(settingsListener)

    if (getEnabledConnectionSettings().isEmpty()) {
      contentManager.addContent(createEmptyContent(project))
      return
    }

    connections.forEach {
      if (it.isEnabled) {
        addToolWindow(it)
      }
    }

    // Cleanup settings for missing connections.
    val connectionIds = connections.map { it.innerId }
    val storedConfigs = KafkaToolWindowSettings.getInstance().configs
    val deprecatedConfigIds = storedConfigs.keys.minus(connectionIds)
    storedConfigs -= deprecatedConfigIds
    storedConfigs.entries.removeIf { !connectionIds.contains(it.key) }

    // Restore current page from settings.
    val found = contentManager.contents.find { it.getUserData(CONNECTION_ID) == KafkaToolWindowSettings.getInstance().selectedConnectionId }
    found?.let { contentManager.setSelectedContent(it) }

    // Subscribe to "Current page change" for saving to settings.
    contentManager.addContentManagerListener(object : ContentManagerListener {
      override fun contentAdded(event: ContentManagerEvent) {}
      override fun contentRemoveQuery(event: ContentManagerEvent) {}
      override fun contentRemoved(event: ContentManagerEvent) {}

      override fun selectionChanged(event: ContentManagerEvent) {
        KafkaToolWindowSettings.getInstance().selectedConnectionId = event.content.getUserData(CONNECTION_ID) ?: ""
      }
    })
  }

  private fun onRefreshIntervalChanged(newInterval: Int) {
    KafkaToolWindowSettings.getInstance().dataUpdateIntervalMillis = newInterval
    getEnabledConnectionSettings().forEach {
      val manager = KafkaDataManager.getInstance(it.innerId, project)?.autoUpdaterManager ?: return@forEach

      if (newInterval <= 0) manager.stopAll()
      else manager.rescheduleAll()
    }
  }


  override fun focusOn(connectionId: String) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

    toolWindow.show {
      val contentManager = toolWindow.contentManager
      val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionId } ?: return@show
      contentManager.setSelectedContent(content)
      contentManager.requestFocus(content, true)
    }
  }

  override fun getEnabledConnectionSettings(): List<KafkaConnectionData> =
    RfsConnectionDataManager.instance?.getTyped<KafkaConnectionData>(project)
      ?.filter { it.isEnabled } ?: emptyList()

  companion object {
    fun getInstance(project: Project): KafkaMonitoringToolWindowController? = project.getService(
      KafkaMonitoringToolWindowController::class.java)

    const val TOOL_WINDOW_ID = "KafkaToolWindow"
  }
}