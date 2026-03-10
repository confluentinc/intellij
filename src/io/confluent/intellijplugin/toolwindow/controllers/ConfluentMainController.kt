package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.PopupHandler
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.util.ui.tree.TreeModelAdapter
import com.intellij.openapi.observable.properties.AtomicProperty
import javax.swing.DefaultComboBoxModel
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsActionPlaces
import io.confluent.intellijplugin.core.util.ToolbarUtils
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.util.KafkaControllerUtils
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isCluster
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistry
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isTopic
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchema
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getClusterId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getEnvironmentId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getSchemaRegistryId
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
import io.confluent.intellijplugin.core.rfs.driver.ConnectedConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.DriverConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsEditorErrorPanel
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsNodeAnimator
import io.confluent.intellijplugin.core.rfs.fileInfo.DriverRfsListener
import io.confluent.intellijplugin.core.rfs.projectview.pane.RfsPaneSpeedSearch
import io.confluent.intellijplugin.core.rfs.tree.DriverRfsTreeModel
import io.confluent.intellijplugin.core.rfs.util.RfsUtil
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.data.CCloudClusterDataManager
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.registry.confluent.controller.KafkaRegistryController
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.toolwindow.NavigableController
import io.confluent.intellijplugin.toolwindow.controllers.TopicsController
import io.confluent.intellijplugin.util.KafkaMessagesBundle.message
import com.intellij.ui.table.JBTable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.Promise
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.event.TreeModelEvent
import javax.swing.table.DefaultTableModel
import javax.swing.tree.TreePath

/**
 * Main controller for Confluent Cloud tool window using RFS tree infrastructure.
 * Manages environment selector, cluster/schema registry tree, and detail views for topics and schemas.
 *
 * Does not extend MainTreeController because the driver must be injected directly
 * instead of retrieved from DriverManager (Confluent Cloud connections are created dynamically).
 */
internal class ConfluentMainController(
    private val project: Project,
    private val driver: ConfluentDriver
) : ComponentController, NavigableController {

    private val dataManager = driver.dataManager

    private val detailsLayout = CardLayout()
    private val details = JPanel(detailsLayout)
    private val isNormalView = AtomicBooleanProperty(true)
    private val isErrorView = isNormalView.not()

    // Environment selector
    private val selectedEnvironmentId = AtomicProperty<String?>(null)
    private val environmentComboBoxModel = DefaultComboBoxModel<EnvironmentItem>()

    private lateinit var myTree: ProjectViewTree
    private lateinit var treeModel: DriverRfsTreeModel
    private lateinit var normalPanel: OnePixelSplitter
    private lateinit var panel: DialogPanel
    private lateinit var component: JComponent
    private lateinit var lastSelectedPath: TreePath

    private var prevError: Throwable? = null
    private val errorPanel = JPanel(BorderLayout())

    private fun createPlaceholderPanel(message: String) = JPanel(BorderLayout()).apply {
        add(JLabel(message, SwingConstants.CENTER), BorderLayout.CENTER)
    }

    private inline fun updatePanel(panel: JPanel, contentBuilder: () -> JComponent) {
        panel.removeAll()
        try {
            panel.add(contentBuilder(), BorderLayout.CENTER)
        } catch (e: Exception) {
            thisLogger().warn("Error updating panel", e)
            panel.add(JLabel(message("table.loading.error", e.message ?: message("error.unknown")), SwingConstants.CENTER), BorderLayout.CENTER)
        }
        panel.revalidate()
        panel.repaint()
    }

    private val emptyDetailsPanel = createPlaceholderPanel(message("confluent.cloud.details.placeholder"))
    private val loadingDetailsPanel = createPlaceholderPanel(message("table.loading"))
    private val environmentDetailsPanel = JPanel(BorderLayout())
    private val topicsDetailsPanel = JPanel(BorderLayout())

    private val topicDetailPanel = JPanel(BorderLayout())

    private val schemasDetailsPanel = JPanel(BorderLayout())

    private val schemaDetailPanel = JPanel(BorderLayout())

    // Controller caching to avoid recreating on every selection
    private val registryControllers = mutableMapOf<String, KafkaRegistryController>()

    // Track currently active detail controllers for refresh support
    private var currentTopicDetailsController: TopicDetailsController? = null
    private var currentSchemaDetailsController: ConfluentSchemaDetailController? = null

    private var hasShownInitialEnvironmentDetails = false

    private val driverListener = object : DriverRfsListener {
        override fun driverRefreshFinished(status: DriverConnectionStatus) {
            invokeLater {
                updateMainPanel(status.getException())

                if (status == ConnectedConnectionStatus) {
                    installToolbarIfNeeded()
                    populateEnvironmentSelector()

                    if (!hasShownInitialEnvironmentDetails) {
                        selectedEnvironmentId.get()?.let { envId ->
                            hasShownInitialEnvironmentDetails = true
                            showEnvironmentDetails(envId)
                            (details.layout as CardLayout).show(details, ENVIRONMENT_PANEL)
                        }
                    }
                } else {
                    hasShownInitialEnvironmentDetails = false
                }
            }
        }
    }

    companion object {
        private const val EMPTY_PANEL = "empty"
        private const val LOADING_PANEL = "loading"
        private const val ENVIRONMENT_PANEL = "environment"
        private const val TOPICS_PANEL = "topics"
        private const val TOPIC_DETAIL_PANEL = "topicDetail"
        private const val SCHEMAS_PANEL = "schemas"
        private const val SCHEMA_DETAIL_PANEL = "schemaDetail"
    }

    override fun dispose() {}

    fun refreshControlPlane() {
        val prevSelectedId = selectedEnvironmentId.get()

        driver.safeExecutor.coroutineScope.launch(Dispatchers.IO) {
            try {
                dataManager.client.refreshEnvironments()

                if (prevSelectedId != null) {
                    dataManager.client.refreshKafkaClusters(prevSelectedId)
                    dataManager.client.refreshSchemaRegistry(prevSelectedId)
                }

                invokeLater {
                    populateEnvironmentSelector()

                    if (prevSelectedId != null) {
                        val envStillExists = environmentComboBoxModel.getSize() > 0 &&
                            (0 until environmentComboBoxModel.size).any {
                                (environmentComboBoxModel.getElementAt(it) as? EnvironmentItem)?.id == prevSelectedId
                            }

                        if (envStillExists) {
                            selectedEnvironmentId.set(prevSelectedId)
                            environmentComboBoxModel.selectedItem = environmentComboBoxModel.run {
                                (0 until size).map { getElementAt(it) as EnvironmentItem }
                                    .find { it.id == prevSelectedId }
                            }

                            driver.selectedEnvironmentId = prevSelectedId
                            dataManager.cancelAllEnrichmentJobs()
                            dataManager.preInitializeCachesForEnvironment(prevSelectedId) {
                                driver.registerListenersForEnvironment(prevSelectedId)
                                driver.fileInfoManager.refreshFiles(driver.root)
                            }

                            if ((details.layout as? CardLayout)?.let { true } == true) {
                                showEnvironmentDetails(prevSelectedId)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                thisLogger().warn("Failed to refresh control plane", e)
            }
        }
    }

    /** Refresh currently visible schema detail panel. */
    fun refreshDetailPanel() {
        currentSchemaDetailsController?.takeIf { !Disposer.isDisposed(it) }?.refresh()
    }

    fun init() {
        details.add(emptyDetailsPanel, EMPTY_PANEL)
        details.add(loadingDetailsPanel, LOADING_PANEL)
        details.add(environmentDetailsPanel, ENVIRONMENT_PANEL)
        details.add(topicsDetailsPanel, TOPICS_PANEL)
        details.add(topicDetailPanel, TOPIC_DETAIL_PANEL)
        details.add(schemasDetailsPanel, SCHEMAS_PANEL)
        details.add(schemaDetailPanel, SCHEMA_DETAIL_PANEL)

        normalPanel = createNormalPanel()
        panel = panel {
            row {
                cell(normalPanel).align(Align.FILL)
            }.resizableRow().visibleIf(isNormalView)
            row {
                cell(errorPanel).align(Align.FILL)
            }.resizableRow().visibleIf(isErrorView)
        }
        component = UiDataProvider.wrapComponent(panel) { sink ->
            val selectedPath = myTree.selectionPath?.lastDriverNode?.rfsPath

            // Use cluster-specific manager for clusters/topics (enables topic actions)
            // Use cluster-specific manager for schema registries/schemas (enables schema actions)
            val dataManagerToProvide = when {
                selectedPath == null -> dataManager
                selectedPath.isCluster(driver) || selectedPath.isTopic -> {
                    val clusterId = selectedPath.getClusterId()
                    val envId = selectedPath.getEnvironmentId(driver)
                    if (clusterId != null && envId != null) {
                        val cluster = dataManager.getCachedKafkaClusters(envId)?.find { it.id == clusterId }
                        cluster?.let { dataManager.getOrCreateClusterDataManager(it) } ?: dataManager
                    } else {
                        dataManager
                    }
                }
                selectedPath.isSchemaRegistry(driver) || selectedPath.isSchema -> {
                    val envId = selectedPath.getEnvironmentId(driver)
                    if (envId != null) {
                        // SR shared across environment, use any cluster's data manager
                        val cluster = dataManager.getCachedKafkaClusters(envId)?.firstOrNull()
                        cluster?.let { dataManager.getOrCreateClusterDataManager(it) } ?: dataManager
                    } else {
                        dataManager
                    }
                }

                else -> dataManager
            }

            sink[MainTreeController.DATA_MANAGER] = dataManagerToProvide
            sink[MainTreeController.RFS_PATH] = selectedPath
            sink[MainTreeController.NAVIGABLE_CONTROLLER] = this@ConfluentMainController
        }

        driver.addListener(driverListener)
        Disposer.register(this) { driver.removeListener(driverListener) }
        updateMainPanel(dataManager.client.connectionError)
    }

    private fun createNormalPanel(): OnePixelSplitter {
        treeModel = driver.createTreeModel(driver.root, project)
        val asyncTreeModel = AsyncTreeModel(treeModel, this)

        myTree = ProjectViewTree(asyncTreeModel)
        myTree.showsRootHandles = true
        myTree.isRootVisible = false
        DriverRfsTreeModel.fixInitFirstConnection(asyncTreeModel, myTree)

        RfsPaneSpeedSearch.installOn(myTree)
        PopupHandler.installPopupMenu(myTree, "Kafka.Actions", RfsActionPlaces.RFS_PANE_POPUP)
        val nodeAnimator = RfsNodeAnimator(treeModel).also {
            Disposer.register(this, it)
        }
        nodeAnimator.setRepainter { _, rfsPath ->
            val treePath = treeModel.getTreePath(rfsPath) ?: return@setRepainter
            val pathBounds = myTree.getPathBounds(treePath) ?: return@setRepainter
            myTree.repaint(pathBounds)
        }

        myTree.addTreeSelectionListener {
            val treePath = it.newLeadSelectionPath
            if (treePath != null)
                lastSelectedPath = treePath

            val rfsPath = treePath?.lastDriverNode?.rfsPath
            showDetailsComponent(rfsPath)
        }

        return OnePixelSplitter().apply {
            proportion = 0.25f
            dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_PROPORTION
            firstComponent = createTreePanel()
            secondComponent = details
        }
    }

    private lateinit var treePanel: SimpleToolWindowPanel
    private var toolbarInstalled = false

    private fun createTreePanel(): SimpleToolWindowPanel {
        val scroll = JBScrollPane(myTree).apply {
            border = IdeBorderFactory.createBorder(SideBorder.LEFT)
        }

        val selectorPanel = panel {
            row {
                label(message("confluent.cloud.environment.selector.label")).gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
                comboBox(environmentComboBoxModel)
                    .also { comboBoxComponent ->
                        comboBoxComponent.component.addActionListener {
                            val selected = environmentComboBoxModel.selectedItem as? EnvironmentItem
                            if (selected != null && selected.id != selectedEnvironmentId.get()) {
                                selectedEnvironmentId.set(selected.id)
                                refreshTreeForEnvironment(selected.id)
                            }
                        }
                    }
            }
        }.apply {
            border = JBUI.Borders.empty(4, 8)
        }

        val contentPanel = JPanel(BorderLayout()).apply {
            add(selectorPanel, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        treePanel = SimpleToolWindowPanel(true, true)
        treePanel.setContent(contentPanel)

        return treePanel
    }

    private fun installToolbarIfNeeded() {
        if (toolbarInstalled) return
        toolbarInstalled = true

        val toolbar = ToolbarUtils.createActionToolbar(
            "ConfluentMainController",
            KafkaControllerUtils.createTopicToolbar(),
            true
        )
        toolbar.targetComponent = treePanel
        treePanel.toolbar = toolbar.component
    }

    private fun populateEnvironmentSelector() {
        val environments = dataManager.client.getEnvironments()

        environmentComboBoxModel.removeAllElements()

        environments.forEach { env ->
            environmentComboBoxModel.addElement(EnvironmentItem(env.id, env.displayName))
        }

        if (environments.isNotEmpty() && selectedEnvironmentId.get() == null) {
            val firstEnv = environments.first()
            driver.selectedEnvironmentId = firstEnv.id
            selectedEnvironmentId.set(firstEnv.id)
            environmentComboBoxModel.selectedItem = environmentComboBoxModel.getElementAt(0)

            dataManager.preInitializeCachesForEnvironment(firstEnv.id) {
                driver.registerListenersForEnvironment(firstEnv.id)
                driver.fileInfoManager.refreshFiles(driver.root)
            }
        }
    }

    private fun refreshTreeForEnvironment(envId: String) {
        driver.selectedEnvironmentId = envId
        myTree.clearSelection()

        dataManager.cancelAllEnrichmentJobs()

        dataManager.preInitializeCachesForEnvironment(envId) {
            if (driver.selectedEnvironmentId == envId) {
                driver.registerListenersForEnvironment(envId)
                driver.fileInfoManager.refreshFiles(driver.root)
            }
        }

        invokeLater {
            com.intellij.util.ui.tree.TreeUtil.collapseAll(myTree, 0)
            showEnvironmentDetails(envId)
            (details.layout as CardLayout).show(details, ENVIRONMENT_PANEL)
        }
    }

    private data class EnvironmentItem(val id: String, val displayName: String) {
        override fun toString() = displayName
    }

    private fun showDetailsComponent(rfsPath: RfsPath?) {
        val layout = details.layout as CardLayout
        when {
            rfsPath == null -> {
                val envId = selectedEnvironmentId.get()
                if (envId != null) {
                    showEnvironmentDetails(envId)
                    layout.show(details, ENVIRONMENT_PANEL)
                } else {
                    layout.show(details, EMPTY_PANEL)
                }
            }

            rfsPath.isCluster(driver) -> {
                showTopicsDetails(rfsPath)
                layout.show(details, TOPICS_PANEL)
            }

            rfsPath.isTopic -> {
                showTopicDetail(rfsPath)
                layout.show(details, TOPIC_DETAIL_PANEL)
            }

            rfsPath.isSchemaRegistry(driver) -> {
                showSchemasDetails(rfsPath)
                layout.show(details, SCHEMAS_PANEL)
            }

            rfsPath.isSchema -> {
                showSchemaDetail(rfsPath)
                layout.show(details, SCHEMA_DETAIL_PANEL)
            }

            else -> layout.show(details, EMPTY_PANEL)
        }
    }

    private fun showEnvironmentDetails(envId: String) {
        updatePanel(environmentDetailsPanel) {
            val environment = dataManager.client.getEnvironments()
                .find { it.id == envId }
                ?: throw IllegalStateException("Environment not found")

            createEnvironmentTable(environment)
        }
    }

    private fun createEnvironmentTable(environment: Environment): JComponent {
        val data = arrayOf(
            arrayOf<Any>(message("confluent.cloud.environment.table.id"), environment.id),
            arrayOf<Any>(message("confluent.cloud.environment.table.name"), environment.displayName),
            arrayOf<Any>(
                message("confluent.cloud.environment.table.governance"),
                environment.streamGovernancePackage ?: "N/A"
            )
        )

        val table = JBTable(
            DefaultTableModel(
                data,
                arrayOf(message("confluent.cloud.table.column.property"), message("confluent.cloud.table.column.value"))
            )
        ).apply {
            setDefaultEditor(Any::class.java, null)
            tableHeader = null
        }

        return JBScrollPane(table)
    }

    private fun showTopicsDetails(rfsPath: RfsPath) {
        updatePanel(topicsDetailsPanel) {
            val envId = rfsPath.getEnvironmentId(driver)
                ?: return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.select.resource"))
            val clusterId = rfsPath.getClusterId()
                ?: return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.select.resource"))

            val cluster = dataManager.getCachedKafkaClusters(envId)?.find { it.id == clusterId }
            if (cluster == null) {
                myTree.clearSelection()
                return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.resource.not.available"))
            }

            val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)

            val topicsController = TopicsController(project, clusterDataManager, this)
            Disposer.register(this, topicsController)

            topicsController.getComponent()
        }
    }

    private fun showTopicDetail(rfsPath: RfsPath) {
        topicDetailPanel.removeAll()
        currentTopicDetailsController?.let { Disposer.dispose(it) }
        currentTopicDetailsController = null

        val envId = rfsPath.getEnvironmentId(driver) ?: run {
            topicDetailPanel.add(JLabel(message("confluent.cloud.details.select.resource"), SwingConstants.CENTER), BorderLayout.CENTER)
            topicDetailPanel.revalidate()
            topicDetailPanel.repaint()
            return
        }

        val clusterId = rfsPath.getClusterId() ?: run {
            topicDetailPanel.add(JLabel(message("confluent.cloud.details.select.resource"), SwingConstants.CENTER), BorderLayout.CENTER)
            topicDetailPanel.revalidate()
            topicDetailPanel.repaint()
            return
        }

        val topicName = rfsPath.name

        val cluster = dataManager.getCachedKafkaClusters(envId)?.find { it.id == clusterId }
        if (cluster == null) {
            myTree.clearSelection()
            topicDetailPanel.add(JLabel(message("confluent.cloud.details.resource.not.available"), SwingConstants.CENTER), BorderLayout.CENTER)
            topicDetailPanel.revalidate()
            topicDetailPanel.repaint()
            return
        }

        val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)

        val detailsController = TopicDetailsController(project, clusterDataManager)
        Disposer.register(this, detailsController)

        detailsController.setDetailsId(topicName)
        currentTopicDetailsController = detailsController

        topicDetailPanel.add(detailsController.getComponent(), BorderLayout.CENTER)

        topicDetailPanel.revalidate()
        topicDetailPanel.repaint()
    }

    private fun showSchemasDetails(rfsPath: RfsPath) {
        updatePanel(schemasDetailsPanel) {
            val envId = rfsPath.getEnvironmentId(driver)
                ?: return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.select.resource"))
            val srId = rfsPath.getSchemaRegistryId()
                ?: return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.select.resource"))

            val sr = dataManager.getCachedSchemaRegistry(envId)
            if (sr == null || sr.id != srId) {
                myTree.clearSelection()
                return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.resource.not.available"))
            }

            val cluster = dataManager.getCachedKafkaClusters(envId)?.firstOrNull()
                ?: return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.no.clusters"))

            val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)

            if (clusterDataManager.registryType == KafkaRegistryType.NONE) {
                return@updatePanel createPlaceholderPanel(message("confluent.cloud.details.no.schema.registry"))
            }

            clusterDataManager.initRefreshSchemasIfRequired()

            val registryController = getOrCreateRegistryController(srId, clusterDataManager)
            registryController.getComponent()
        }
    }

    private fun showSchemaDetail(rfsPath: RfsPath) {
        schemaDetailPanel.removeAll()
        currentSchemaDetailsController?.let { Disposer.dispose(it) }
        currentSchemaDetailsController = null

        val envId = rfsPath.getEnvironmentId(driver) ?: run {
            schemaDetailPanel.add(JLabel(message("confluent.cloud.details.select.resource"), SwingConstants.CENTER), BorderLayout.CENTER)
            schemaDetailPanel.revalidate()
            schemaDetailPanel.repaint()
            return
        }

        val srId = rfsPath.getSchemaRegistryId() ?: run {
            schemaDetailPanel.add(JLabel(message("confluent.cloud.details.select.resource"), SwingConstants.CENTER), BorderLayout.CENTER)
            schemaDetailPanel.revalidate()
            schemaDetailPanel.repaint()
            return
        }

        val subjectName = rfsPath.name

        val sr = dataManager.getCachedSchemaRegistry(envId)
        if (sr == null || sr.id != srId) {
            myTree.clearSelection()
            schemaDetailPanel.add(JLabel(message("confluent.cloud.details.resource.not.available"), SwingConstants.CENTER), BorderLayout.CENTER)
            schemaDetailPanel.revalidate()
            schemaDetailPanel.repaint()
            return
        }

        val cluster = dataManager.getCachedKafkaClusters(envId)?.firstOrNull()
        if (cluster == null) {
            schemaDetailPanel.add(JLabel(message("confluent.cloud.details.no.clusters"), SwingConstants.CENTER), BorderLayout.CENTER)
            schemaDetailPanel.revalidate()
            schemaDetailPanel.repaint()
            return
        }

        val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)

        if (clusterDataManager.registryType == KafkaRegistryType.NONE) {
            schemaDetailPanel.add(JLabel(message("confluent.cloud.details.no.schema.registry"), SwingConstants.CENTER), BorderLayout.CENTER)
            schemaDetailPanel.revalidate()
            schemaDetailPanel.repaint()
            return
        }

        val detailsController = ConfluentSchemaDetailController(project, clusterDataManager)
        Disposer.register(this, detailsController)
        detailsController.setDetailsId(subjectName)
        currentSchemaDetailsController = detailsController

        schemaDetailPanel.add(detailsController.getComponent(), BorderLayout.CENTER)

        schemaDetailPanel.revalidate()
        schemaDetailPanel.repaint()
    }

    override fun open(rfsPath: RfsPath): Promise<TreePath> {
        return RfsUtil.select(driver.getExternalId(), rfsPath, myTree)
    }

    override fun getComponent(): JComponent = component

    private fun getOrCreateRegistryController(srId: String, clusterDataManager: CCloudClusterDataManager): KafkaRegistryController {
        return registryControllers.getOrPut(srId) {
            KafkaRegistryController(project, clusterDataManager, this).also {
                Disposer.register(this, it)
            }
        }
    }

    private fun updateMainPanel(exception: Throwable?) {
        if (exception == null) {
            isNormalView.set(true)
        } else {
            isNormalView.set(false)
            setErrorPanel(exception)
        }
    }

    private fun setErrorPanel(exception: Throwable) {
        if (prevError == exception)
            return
        prevError = exception
        errorPanel.removeAll()
        errorPanel.add(RfsEditorErrorPanel(exception, this, driver), BorderLayout.CENTER)
        errorPanel.revalidate()
        errorPanel.repaint()
    }
}
