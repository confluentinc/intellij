package io.confluent.intellijplugin.toolwindow

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController
import io.confluent.intellijplugin.core.rfs.driver.ActivitySource
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.driver.refreshConnectionLaunch
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.toolwindow.controllers.ConfluentMainController
import io.confluent.intellijplugin.toolwindow.controllers.ConfluentTabController
import io.confluent.intellijplugin.toolwindow.controllers.KafkaMainController
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
class KafkaMonitoringToolWindowController(project: Project) : MonitoringToolWindowController(project) {
    override val settings
        get() = KafkaToolWindowSettings.getInstance()

    override val helpTopicId: String = "big.data.tools.kafka"

    override val toolWindowId: String = TOOL_WINDOW_ID

    private val settingsListener = KafkaConnectionSettingsListener()

    override fun createConnectionGroup(): ConnectionFactory<*> = KafkaConnectionGroup()

    override fun isSupportedData(connectionData: ConnectionData): Boolean =
        connectionData is KafkaConnectionData || connectionData is ConfluentConnectionData

    override fun createMainController(connectionData: ConnectionData): ComponentController = when (connectionData) {
        is KafkaConnectionData -> KafkaMainController(project, connectionData)
        is ConfluentConnectionData -> {
            val driver = ConfluentDriver(
                connectionData,
                project,
                testConnection = false
            )
            driver.initDriverUpdater()

            val controller = ConfluentMainController(project, driver)
            controller.init()
            // Set controller reference for navigation from tree nodes
            driver.mainController = controller

            com.intellij.openapi.util.Disposer.register(controller, driver)

            controller
        }
        else -> error("Unsupported connection type: ${connectionData::class.simpleName}")
    }

    override fun dispose() {
        super.dispose()
        RfsConnectionDataManager.instance?.removeListener(settingsListener)
    }

    override fun setUp(toolWindow: ToolWindow) {
        super.setUp(toolWindow)
        RfsConnectionDataManager.instance?.addListener(settingsListener)
        KafkaRegistryUtil.disableLoggers()

        fun reapplyIcon() = contentManager.contents.find { it.getUserData(CONNECTION_ID) == "ccloud" }?.let(::applyConfluentCloudIcon)

        contentManager.addContentManagerListener(object : com.intellij.ui.content.ContentManagerListener {
            override fun contentAdded(event: com.intellij.ui.content.ContentManagerEvent) {
                if (event.content.getUserData(CONNECTION_ID) == "ccloud") applyConfluentCloudIcon(event.content)
            }
            override fun selectionChanged(event: com.intellij.ui.content.ContentManagerEvent) { reapplyIcon() }
        })

        project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
            override fun stateChanged(toolWindowManager: ToolWindowManager) {
                if (toolWindowManager.getToolWindow(TOOL_WINDOW_ID)?.isVisible == true) reapplyIcon()
            }
        })

        addConfluentCloudTab()
    }

    override fun onRefreshAction(e: com.intellij.openapi.actionSystem.AnActionEvent) {
        val connectionId = contentManager.selectedContent?.getUserData(CONNECTION_ID) ?: return

        if (connectionId == "ccloud") {
            val tabController = getConfluentCloudTabController()
            val driver = tabController?.getDriver()

            driver?.let {
                it.dataManager.updater.stopAll()
                it.dataManager.cancelAllEnrichmentJobs()

                tabController.getMainController()?.refreshControlPlane()

                it.safeExecutor.coroutineScope.launch {
                    it.dataManager.updater.reloadAll(checkConnection = false)
                }
            }

            tabController?.refreshDetailPanel()
            return
        }

        super.onRefreshAction(e)
    }

    override fun onRefreshIntervalChanged(newInterval: Int) {
        super.onRefreshIntervalChanged(newInterval)

        val ccloudDriver = getConfluentCloudTabController()?.getDriver()
        if (ccloudDriver != null) {
            val updater = ccloudDriver.dataManager.updater
            if (newInterval <= 0) updater.stopAll()
            else updater.rescheduleAll()
        }
    }

    override fun getDriverForToolbar(connectionId: String?): MonitoringDriver? {
        // CCloud driver is not in DriverManager, get it from ConfluentTabController
        if (connectionId == "ccloud") {
            return getConfluentCloudTabController()?.getDriver()
        }
        return super.getDriverForToolbar(connectionId)
    }

    fun getDriverForConnection(connectionId: String): MonitoringDriver? {
        if (connectionId == "ccloud") {
            return getConfluentCloudTabController()?.getDriver()
        }
        return DriverManager.getDriverById(project, connectionId)
            as? MonitoringDriver
    }

    private fun addConfluentCloudTab() {
        if (contentManager.contents.any { it.getUserData(CONNECTION_ID) == "ccloud" }) {
            return
        }

        contentManager.contents
            .filter { it.getUserData(CONNECTION_ID) == null }
            .forEach { contentManager.removeContent(it, true) }

        val controller = ConfluentTabController(
            project,
            onDriverCreated = { refreshCCloudToolbar() }
        )

        val content = contentManager.factory.createContent(
            controller.getComponent(),
            KafkaMessagesBundle.message("confluent.cloud.name"),
            false
        ).apply {
            putUserData(CONNECTION_ID, "ccloud")
            putUserData(PAGE_CONTROLLER_ID, controller)
            putUserData(PROJECT, project)
            isCloseable = false
            icon = BigdatatoolsKafkaIcons.ConfluentTab
        }

        com.intellij.openapi.util.Disposer.register(content, controller)
        contentManager.addContent(content)
        invokeLater { applyConfluentCloudIcon(content) }
    }

    private fun applyConfluentCloudIcon(content: com.intellij.ui.content.Content) {
        val icon = BigdatatoolsKafkaIcons.ConfluentTab
        content.icon = icon
        ComponentUtil.getParentOfType(InternalDecorator::class.java, contentManager.component)?.let {
            UIUtil.findComponentsOfType(it, ContentTabLabel::class.java).find { tab -> tab.content == content }?.apply {
                this.icon = icon
                this.disabledIcon = icon
                iconTextGap = 4
                revalidate()
                repaint()
            }
        }
    }

    fun getConfluentCloudTabController(): ConfluentTabController? {
        val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == "ccloud" }
        return content?.getUserData(PAGE_CONTROLLER_ID) as? ConfluentTabController
    }

    private fun refreshCCloudToolbar() {
        // Refresh toolbar actions to show progress component after CCloud driver is created
        ApplicationManager.getApplication().invokeLater {
            if (contentManager.selectedContent?.getUserData(CONNECTION_ID) == "ccloud") {
                setupActions("ccloud")
            }
        }
    }

    override fun focusOn(connectionId: String) = focusOn(connectionId, null)

    fun focusOn(connectionId: String, rfsPath: RfsPath?) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return

        toolWindow.show {
            val contentManager = toolWindow.contentManager
            val content =
                contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionId } ?: return@show
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
            KafkaMonitoringToolWindowController::class.java
        )

        const val TOOL_WINDOW_ID = "KafkaToolWindow"
    }
}