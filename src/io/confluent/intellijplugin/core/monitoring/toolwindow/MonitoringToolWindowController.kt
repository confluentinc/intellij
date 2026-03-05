package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.InternalDecorator
import com.intellij.openapi.wm.impl.content.ContentTabLabel
import com.intellij.ui.ComponentUtil
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManager
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.ui.StatusText
import com.intellij.util.ui.UIUtil
import io.confluent.intellijplugin.core.connection.updater.IntervalUpdateSettings
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.driver.ActivitySource
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.driver.refreshConnectionLaunch
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.ConnectionSettings
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.connections.ConnectionFactory
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager.Companion.filterOnlyEnabled
import io.confluent.intellijplugin.core.ui.AutorefreshPopupComponent
import io.confluent.intellijplugin.core.ui.CustomComponentActionImpl
import io.confluent.intellijplugin.core.util.ConnectionUtil
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.awt.event.ActionEvent
import javax.swing.BorderFactory

abstract class MonitoringToolWindowController(protected val project: Project) : Disposable {
    // Used to prevent auto-refresh button spam.
    private var lastAutoRefreshTimestamp = 0L
    protected lateinit var contentManager: ContentManager
    private val settingsListener = MonitoringConnectionListener()
    protected abstract val settings: IntervalUpdateSettings
    protected abstract val helpTopicId: String
    protected abstract val toolWindowId: String
    private lateinit var toolWindow: ToolWindow
    private lateinit var refreshAction: CustomComponentActionImpl
    private val openSettingAction = MonitoringSettingsAction()

    init {
        DriverManager.init(project)
    }

    override fun dispose() {
        RfsConnectionDataManager.instance?.removeListener(settingsListener)
        try {
            toolWindow.remove()
        } catch (_: Throwable) {

        }
    }

    open fun setUp(toolWindow: ToolWindow) {
        this.toolWindow = toolWindow
        toolWindow.isAutoHide = false
        toolWindow.setToHideOnEmptyContent(false)

        contentManager = toolWindow.contentManager
        contentManager.addUiDataProvider { sink ->
            sink[PlatformDataKeys.HELP_ID] = helpTopicId
        }

        val autorefresh = AutorefreshPopupComponent().apply {
            isOpaque = false
            value = settings.dataUpdateIntervalMillis
            border = BorderFactory.createEmptyBorder(2, 2, 2, 2)
            onRefreshIntervalChanged = ::onRefreshIntervalChanged
            onActionPerformed = ::onRefreshAction
        }

        autorefresh.registerRefreshShortcut(toolWindow.component)
        refreshAction = CustomComponentActionImpl(autorefresh)

        // Temporary removed, until we will not understand is this a perfect feature or not.
        //actionsGroup.add(object : DumbAwareAction("Open in Editor", null, AllIcons.Actions.Edit) {
        //  override fun displayTextInToolbar(): Boolean = true
        //
        //  override fun actionPerformed(e: AnActionEvent) {
        //    val selectedContent = contentManager.selectedContent ?: return
        //    val connectionId = selectedContent.getUserData(CONNECTION_ID) ?: return
        //    val connectionData = RfsConnectionDataManager.instance?.getConnectionById(project, connectionId) ?: return
        //    val mainController = createMainController(connectionData)
        //    JComponentEditorProvider.openEditor(project, "Title", mainController.getComponent())
        //  }
        //})

        RfsConnectionDataManager.instance?.addListener(settingsListener)

        val connections = getConnectionSettings()

        if (connections.isEmpty()) {
            contentManager.addContent(createEmptyContent(project))
            setupContentManagerListener()
            setupActions(connectionId = null)
            return
        }

        connections.reversed().forEach {
            addContentPane(it)
        }

        cleanUpSettingsForMissedConnections(connections)

        DriverManager.onDriversInit(project) {
            invokeLater {
                // Or settings selection as "First not disabled"
                val found = contentManager.contents.firstOrNull { content ->
                    val connectionId = content.getUserData(CONNECTION_ID) ?: return@firstOrNull false
                    connections.firstOrNull { it.innerId == connectionId }?.isEnabled == true
                }

                found?.let { setSelectedContent(it) }
            }
        }

        setupContentManagerListener()
        setupActions(connectionId = null)

        // This is a very complicated fix for BDIDE-4707. We have some toolbar component which are removed from toolbar
        // depending of context. So we will update them all on LAF change.
        ApplicationManager.getApplication().messageBus.connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener {
                contentManager.contents.forEach {
                    val connectionId = it.getUserData(CONNECTION_ID) ?: return@forEach
                    val dataManager =
                        (DriverManager.getDriverById(project, connectionId) as? MonitoringDriver)?.dataManager
                            ?: return@forEach
                    dataManager.progressComponent.component.updateUI()
                }
            })
    }

    // Subscribe to "Current page change" for saving to settings last opened page
    private fun setupContentManagerListener() {
        contentManager.addContentManagerListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                if (event.isConsumed || event.operation == ContentManagerEvent.ContentOperation.remove) {
                    return
                }
                val connectionId = event.content.getUserData(CONNECTION_ID) ?: ""
                settings.selectedConnectionId = connectionId
                setupActions(connectionId)
            }
        })
    }

    // Allow subclasses to provide custom driver lookup (e.g., CCloud driver not in DriverManager)
    protected open fun getDriverForToolbar(connectionId: String?): MonitoringDriver? {
        return if (connectionId == null) null
        else DriverManager.getDriverById(project, connectionId) as? MonitoringDriver
    }

    protected fun setupActions(connectionId: String?) {
        val dataManager = getDriverForToolbar(connectionId)?.dataManager
        val progressComponent = dataManager?.progressComponent?.component


        // Plus action after the last tab and grayed title of a currently selected component.
        val tabActions = listOfNotNull(CreateNewConnectionAction())
        (toolWindow as? ToolWindowEx)?.setTabActions(DefaultActionGroup(tabActions))

        (toolWindow as? ToolWindowEx)?.setTitleActions(
            listOfNotNull(
                if (progressComponent == null) null else CustomComponentActionImpl(progressComponent),
                refreshAction,
                Separator.create(),
                openSettingAction
            )
        )
    }

    protected fun setSelectedContent(content: Content) {
        val connectionId = content.getUserData(CONNECTION_ID)
        if (connectionId != null) {
            setupActions(connectionId)
        }
        contentManager.setSelectedContent(content)
    }

    protected abstract fun createConnectionGroup(): ConnectionFactory<*>
    protected abstract fun isSupportedData(connectionData: ConnectionData): Boolean

    open fun focusOn(connectionId: String) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(toolWindowId) ?: return

        toolWindow.show {
            val contentManager = toolWindow.contentManager
            val content =
                contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionId } ?: return@show
            setSelectedContent(content)
        }
    }

    protected open fun onRefreshAction(@Suppress("UNUSED_PARAMETER") e: AnActionEvent) {
        if (System.currentTimeMillis() - lastAutoRefreshTimestamp < AUTOREFRESH_CLICK_INTERVAL)
            return
        lastAutoRefreshTimestamp = System.currentTimeMillis()

        val connectionId = contentManager.selectedContent?.getUserData(CONNECTION_ID) ?: return
        val driver = DriverManager.getDriverById(project, connectionId) as? MonitoringDriver
        if (driver != null) {
            driver.refreshConnectionLaunch(ActivitySource.ACTION)
        }
    }

    private fun removeContentPane(removedConnectionData: ConnectionData) {
        val contentToRemove =
            contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == removedConnectionData.innerId }

        if (contentToRemove != null && contentManager.contents.size == 1) {
            contentManager.addContent(createEmptyContent(project))
        }

        contentToRemove?.let { contentManager.removeContent(it, true) }
    }

    private fun addContentPane(connectionData: ConnectionData, indexToInsert: Int = -1) {
        // Removing empty state.
        val contentToRemove = contentManager.contents.filter { it.getUserData(CONNECTION_ID) == null }
        contentToRemove.forEach { contentManager.removeContent(it, true) }

        DriverManager.onDriversInit(project) {
            invokeLater {
                if (connectionData.isEnabled &&
                    RfsConnectionDataManager.instance?.getConnections(project)
                        ?.any { it.innerId == connectionData.innerId } == true
                ) {
                    val mainController = createMainController(connectionData)
                    val content =
                        contentManager.factory.createContent(mainController.getComponent(), connectionData.name, false)
                            .apply {
                                putUserData(CONNECTION_ID, connectionData.innerId)
                                putUserData(PAGE_CONTROLLER_ID, mainController)
                                putUserData(PROJECT, project)
                                isCloseable = false
                            }
                    Disposer.register(content, mainController)
                    Disposer.register(this, content)
                    if (indexToInsert == -1) {
                        contentManager.addContent(content)
                    } else {
                        contentManager.addContent(content, indexToInsert)
                    }
                    setSelectedContent(content)
                } else {
                    val content = createDisabledContent(project, connectionData)
                    contentManager.addContent(content)
                    setSelectedContent(content)

                    // No other way to access tabs and change tab text color.
                    val internalDecorator =
                        ComponentUtil.getParentOfType(InternalDecorator::class.java, contentManager.component)
                            ?: return@invokeLater
                    val tabs = UIUtil.findComponentsOfType(internalDecorator, ContentTabLabel::class.java)
                    tabs.find { it.content == content }?.apply {
                        isEnabled = false
                    }
                }
            }
        }
    }

    abstract fun createMainController(connectionData: ConnectionData): ComponentController

    private fun cleanUpSettingsForMissedConnections(connections: List<ConnectionData>) {
        val connectionIds = connections.map { it.innerId }
        val storedConfigs = settings.configs
        val deprecatedConfigIds = storedConfigs.keys.minus(connectionIds.toSet())
        storedConfigs -= deprecatedConfigIds
        storedConfigs.entries.removeIf { !connectionIds.contains(it.key) }
    }

    private fun getDataManager(connectionData: ConnectionData) =
        (DriverManager.getDriverById(project, connectionData.innerId) as? MonitoringDriver)?.dataManager

    private fun createEmptyContent(project: Project): Content {
        val settingsOpener: (ActionEvent) -> Unit = {
            ConnectionSettings.create(project, createConnectionGroup())
        }

        val panel = JBPanelWithEmptyText().apply {
            emptyText.apply {
                appendText(KafkaMessagesBundle.message("toolwindow.empty.text"), StatusText.DEFAULT_ATTRIBUTES)
                appendSecondaryText(
                    KafkaMessagesBundle.message("toolwindow.empty.link"),
                    SimpleTextAttributes.LINK_ATTRIBUTES,
                    settingsOpener
                )
                appendSecondaryText(
                    " " + KafkaMessagesBundle.message("toolwindow.empty.secondaryText"),
                    StatusText.DEFAULT_ATTRIBUTES,
                    null
                )
                isShowAboveCenter = false
            }
        }

        return contentManager.factory.createContent(panel, "", true).apply {
            putUserData(Content.TEMPORARY_REMOVED_KEY, true)
            isCloseable = false
        }
    }

    private fun createDisabledContent(project: Project, connectionData: ConnectionData): Content {
        val settingsOpener: (ActionEvent) -> Unit = {
            ConnectionUtil.modifyConnection(project, connectionData, isEnable = true)
        }

        val panel = JBPanelWithEmptyText().apply {
            emptyText.apply {
                appendText(
                    KafkaMessagesBundle.message("services.panel.connection.disabled", connectionData.name),
                    StatusText.DEFAULT_ATTRIBUTES
                )
                appendSecondaryText(
                    KafkaMessagesBundle.message("depend.connection.action.enable"),
                    SimpleTextAttributes.LINK_ATTRIBUTES,
                    settingsOpener
                )
                isShowAboveCenter = false
            }
        }

        return contentManager.factory.createContent(panel, connectionData.name, true).apply {
            putUserData(CONNECTION_ID, connectionData.innerId)
            putUserData(PROJECT, project)
            isCloseable = false
        }
    }

    protected open fun onRefreshIntervalChanged(newInterval: Int) {
        settings.dataUpdateIntervalMillis = newInterval
        getEnabledConnectionSettings().forEach {
            val manager = getDataManager(it)?.updater ?: return@forEach

            if (newInterval <= 0) manager.stopAll()
            else manager.rescheduleAll()
        }
    }

    private fun getConnectionSettings(): List<ConnectionData> {
        val groupId = createConnectionGroup().id
        return RfsConnectionDataManager.instance
            ?.getConnectionsByGroupId(groupId, project)
            ?: emptyList()
    }

    private fun getEnabledConnectionSettings(): List<ConnectionData> {
        val groupId = createConnectionGroup().id
        return RfsConnectionDataManager.instance
            ?.getConnectionsByGroupId(groupId, project)?.filterOnlyEnabled(project) ?: emptyList()
    }

    inner class MonitoringConnectionListener : ConnectionSettingsListener {
        override fun onConnectionAdded(project: Project?, newConnectionData: ConnectionData) {
            if (!isSupportedEvent(project, newConnectionData))
                return

            addContentPane(newConnectionData)
        }

        override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
            if (!isSupportedEvent(project, removedConnectionData))
                return

            removeContentPane(removedConnectionData)
        }

        override fun onConnectionModified(
            project: Project?,
            connectionData: ConnectionData,
            modified: Collection<ModificationKey>
        ) {
            if (!isSupportedEvent(project, connectionData))
                return

            if (modified.contains(CommonSettingsKeys.ENABLED_KEY) && connectionData.isEnabled) {
                toolWindow.show()
            }

            val contentToInsert =
                contentManager.contents.firstOrNull { it.getUserData(CONNECTION_ID) == connectionData.innerId }
            val indexToInsert = if (contentToInsert == null) -1 else contentManager.getIndexOfContent(contentToInsert)

            removeContentPane(connectionData)
            addContentPane(connectionData, indexToInsert)
        }

        private fun isSupportedEvent(
            project: Project?,
            removedConnectionData: ConnectionData
        ): Boolean {
            val isCorrectProject = project == null || this@MonitoringToolWindowController.project == project
            return isCorrectProject && isSupportedData(removedConnectionData)
        }
    }

    inner class MonitoringSettingsAction : DumbAwareAction(
        KafkaMessagesBundle.message("open.settings.action.text"),
        KafkaMessagesBundle.message("open.settings.action.description"),
        AllIcons.General.Settings
    ) {
        init {
            templatePresentation.putClientProperty(ActionUtil.SHOW_TEXT_IN_TOOLBAR, true)
        }

        override fun actionPerformed(e: AnActionEvent) {
            ConnectionSettings.open(project, contentManager.selectedContent?.getUserData(CONNECTION_ID))
        }
    }

    inner class CreateNewConnectionAction : DumbAwareAction(
        KafkaMessagesBundle.message("toolwindow.tab.add.title"),
        KafkaMessagesBundle.message("toolwindow.tab.add"),
        AllIcons.General.Add
    ) {

        override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            ConnectionSettings.create(project, createConnectionGroup())
        }
    }

    companion object {
        /** Interval in milliseconds defines how often we can press refresh button. */
        private const val AUTOREFRESH_CLICK_INTERVAL = 3000

        val PAGE_CONTROLLER_ID = Key.create<ComponentController>("PAGE_CONTROLLER_ID")
        val CONNECTION_ID = Key.create<String>("CONNECTION_ID")
        val PROJECT = Key.create<Project>("PROJECT")
    }
}