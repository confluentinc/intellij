package io.confluent.intellijplugin.toolwindow

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MonitoringToolWindowController
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.icons.BigdatatoolsKafkaIcons
import io.confluent.intellijplugin.registry.KafkaRegistryUtil
import io.confluent.intellijplugin.rfs.ConfluentConnectionData
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.rfs.KafkaConnectionData
import io.confluent.intellijplugin.settings.KafkaConnectionGroup
import io.confluent.intellijplugin.settings.app.KafkaPluginSettings
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

            Disposer.register(controller, driver)

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
        addConfluentCloudTab()
    }

    override fun onRefreshAction(e: AnActionEvent) {
        val connectionId = contentManager.selectedContent?.getUserData(CONNECTION_ID) ?: return

        if (connectionId == "ccloud") {
            val driver = getConfluentCloudTabController()?.getDriver()

            driver?.let {
                it.dataManager.updater.stopAll()
                it.dataManager.cancelAllEnrichmentJobs()

                it.safeExecutor.coroutineScope.launch {
                    it.dataManager.getAllClusterDataManagers().forEach { clusterDataManager ->
                        clusterDataManager.getDataPlaneCache().clearTopicCache()
                        clusterDataManager.getDataPlaneCache().clearSchemaCache()
                        clusterDataManager.clearAllVersionCaches()
                    }

                    it.dataManager.updater.reloadAll(checkConnection = false)

                    it.dataManager.getAllClusterDataManagers().forEach { clusterDataManager ->
                        val versionModels = clusterDataManager.schemaVersionModels.getModelsForRefresh()
                        versionModels.forEach { model ->
                            clusterDataManager.updater.invokeRefreshModel(model)
                        }

                        val partitionModels = clusterDataManager.topicPartitionsModels.getModelsForRefresh()
                        partitionModels.forEach { model ->
                            clusterDataManager.updater.invokeRefreshModel(model)
                        }
                    }
                }
            }
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

    internal fun addConfluentCloudTab() {
        if (KafkaPluginSettings.getInstance().hideConfluentCloudTab) {
            return
        }

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
            putUserData(ToolWindow.SHOW_CONTENT_ICON, true)
            isCloseable = false
            icon = BigdatatoolsKafkaIcons.ConfluentTab
        }

        Disposer.register(content, controller)
        contentManager.addContent(content)
    }

    internal fun removeConfluentCloudTab() {
        val content = contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == "ccloud" } ?: return
        contentManager.removeContent(content, true)
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