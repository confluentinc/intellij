package io.confluent.intellijplugin.toolwindow

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController
import io.confluent.intellijplugin.core.rfs.driver.ActivitySource
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.refreshConnectionLaunch
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import io.confluent.intellijplugin.toolwindow.config.KafkaToolWindowSettings
import io.confluent.intellijplugin.toolwindow.controllers.ConfluentMainController
import io.confluent.intellijplugin.toolwindow.controllers.KafkaMainController

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
            val driver = io.confluent.intellijplugin.rfs.ConfluentDriver(
                connectionData,
                project,
                testConnection = false
            )
            driver.initDriverUpdater()

            val controller = ConfluentMainController(project, driver)
            controller.init()

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
            getConfluentCloudTabController()?.getDriver()?.refreshConnectionLaunch(ActivitySource.ACTION)
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

    fun getDriverForConnection(connectionId: String): io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver? {
        if (connectionId == "ccloud") {
            return getConfluentCloudTabController()?.getDriver()
        }
        return io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager.getDriverById(project, connectionId)
            as? io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
    }

    private fun addConfluentCloudTab() {
        if (contentManager.contents.any { it.getUserData(CONNECTION_ID) == "ccloud" }) {
            return
        }

        contentManager.contents
            .filter { it.getUserData(CONNECTION_ID) == null }
            .forEach { contentManager.removeContent(it, true) }

        val controller = io.confluent.intellijplugin.toolwindow.controllers.ConfluentTabController(project)

        val content = contentManager.factory.createContent(
            controller.getComponent(),
            "Confluent Cloud",
            false
        ).apply {
            putUserData(CONNECTION_ID, "ccloud")
            putUserData(PAGE_CONTROLLER_ID, controller)
            putUserData(PROJECT, project)
            isCloseable = false
        }

        com.intellij.openapi.util.Disposer.register(content, controller)
        contentManager.addContent(content)
        io.confluent.intellijplugin.core.util.invokeLater { applyConfluentCloudIcon(content) }
    }

    private fun applyConfluentCloudIcon(content: com.intellij.ui.content.Content) {
        val icon = io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons.ConfluentTab
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

    fun getConfluentCloudTabController(): io.confluent.intellijplugin.toolwindow.controllers.ConfluentTabController? {
        val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == "ccloud" }
        return content?.getUserData(PAGE_CONTROLLER_ID) as? io.confluent.intellijplugin.toolwindow.controllers.ConfluentTabController
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