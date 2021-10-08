package com.jetbrains.bigdatatools.kafka.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.StatusText
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionGroup
import com.jetbrains.bigdatatools.kafka.toolwindow.config.KafkaToolWindowSettings
import com.jetbrains.bigdatatools.kafka.toolwindow.controllers.ClusterPageController
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.toolwindow.MonitoringToolWindowController
import com.jetbrains.bigdatatools.settings.ConnectionSettings
import com.jetbrains.bigdatatools.settings.ConnectionSettingsListener
import com.jetbrains.bigdatatools.settings.ModificationKey
import com.jetbrains.bigdatatools.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.ui.AutorefreshPopupComponent
import com.jetbrains.bigdatatools.ui.CustomComponentActionImpl
import com.jetbrains.bigdatatools.util.invokeLater
import java.awt.event.ActionEvent
import javax.swing.BorderFactory

class KafkaMonitoringToolWindowController(project: Project) : MonitoringToolWindowController(project) {
  private lateinit var contentManager: ContentManager

  private val settingsListener = object : ConnectionSettingsListener {
    override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
      if (!isSupportedEvent(project, newConnectionData))
        return

      addToolWindow(newConnectionData as KafkaConnectionData)
    }

    override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
      if (!isSupportedEvent(project, removedConnectionData))
        return

      removeToolWindow(removedConnectionData as KafkaConnectionData)
    }

    override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {
      if (!isSupportedEvent(project, connectionData))
        return

      removeToolWindow(connectionData as KafkaConnectionData)
      addToolWindow(connectionData)
    }

    private fun isSupportedEvent(project: Project?,
                                 removedConnectionData: ConnectionData): Boolean {
      val isCorrectProject = project == null || this@KafkaMonitoringToolWindowController.project == project
      return isCorrectProject && removedConnectionData is KafkaConnectionData
    }
  }


  private fun createEmptyContent(project: Project): Content {
    val settingsOpener: (ActionEvent) -> Unit = {
      ConnectionSettings.create(project, KafkaConnectionGroup())
    }

    val panel = JBPanelWithEmptyText()

    val emptyText = panel.emptyText
    emptyText.appendText(KafkaMessagesBundle.message("toolwindow.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
    emptyText.appendSecondaryText(KafkaMessagesBundle.message("toolwindow.empty.link"), SimpleTextAttributes.LINK_ATTRIBUTES,
                                  settingsOpener)
    emptyText.appendSecondaryText(" " + KafkaMessagesBundle.message("toolwindow.empty.secondaryText"), StatusText.DEFAULT_ATTRIBUTES, null)
    emptyText.isShowAboveCenter = false

    return contentManager.factory.createContent(panel, "", true).apply {
      isCloseable = false
    }
  }

  override fun dispose() {
    RfsConnectionDataManager.instance?.removeListener(settingsListener)
  }

  private fun removeToolWindow(removedConnectionData: KafkaConnectionData) {
    val contentToRemove = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == removedConnectionData.innerId }
    contentToRemove?.let { contentManager.removeContent(it, true) }

    if (contentManager.contents.isEmpty()) {
      contentManager.addContent(createEmptyContent(project))
    }
  }

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

      if (newInterval <= 0)
        manager.stopAll()
      else
        manager.rescheduleAll()
    }
  }

  /** Add a single tab for connection-application or multiple tabs if connection has several applications. */
  private fun addToolWindow(connectionData: KafkaConnectionData) {

    if (!connectionData.isEnabled)
      return

    // Removing empty state.
    val contentToRemove = contentManager.contents.filter { it.getUserData(CONNECTION_ID) == null }
    contentToRemove.forEach { contentManager.removeContent(it, true) }

    invokeLater {
      KafkaDataManager.getInstance(connectionData.innerId, project) ?: return@invokeLater

      val clusterPageController = ClusterPageController(project, connectionData)
      val content = contentManager.factory.createContent(clusterPageController.getComponent(), connectionData.name, false)
      content.putUserData(CONNECTION_ID, connectionData.innerId)
      content.putUserData(PAGE_CONTROLLER_ID, clusterPageController)
      content.isCloseable = false
      contentManager.addContent(content)
      contentManager.setSelectedContent(content)
      Disposer.register(content, clusterPageController)
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
    RfsConnectionDataManager.instance?.getTyped<KafkaConnectionData>(project)?.filter { it.isEnabled } ?: emptyList()

  companion object {
    fun getInstance(project: Project): KafkaMonitoringToolWindowController? =
      project.getService(KafkaMonitoringToolWindowController::class.java)

    const val TOOL_WINDOW_ID = "KafkaToolWindow"

    private val helpIdProvider = DataProvider { dataId: String? ->
      if (PlatformDataKeys.HELP_ID.`is`(dataId)) "big.data.tools.kafka" else null
    }

    /** Key which will be added to every content in tool window to properly update tabs when connection settings will be changed.*/
    private val CONNECTION_ID = Key.create<String>("CONNECTION_ID")
    private val PAGE_CONTROLLER_ID = Key.create<ClusterPageController>("PAGE_CONTROLLER_ID")

    /** Interval in milliseconds defines how often we can press refresh button. */
    private const val AUTOREFRESH_CLICK_INTERVAL = 3000
  }
}