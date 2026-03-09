package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
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
import io.confluent.intellijplugin.ccloud.model.Environment
import io.confluent.intellijplugin.toolwindow.NavigableController
import io.confluent.intellijplugin.toolwindow.controllers.TopicsController
import io.confluent.intellijplugin.util.KafkaMessagesBundle.message
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
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
    private lateinit var panel: com.intellij.openapi.ui.DialogPanel
    private lateinit var component: JComponent
    private lateinit var lastSelectedPath: TreePath

    private var prevError: Throwable? = null
    private val errorPanel = JPanel(BorderLayout())

    private fun createPlaceholderPanel(message: String) = JPanel(BorderLayout()).apply {
        add(JLabel(message), BorderLayout.CENTER)
    }

    private inline fun updatePanel(panel: JPanel, contentBuilder: () -> JComponent) {
        panel.removeAll()
        try {
            panel.add(contentBuilder(), BorderLayout.CENTER)
        } catch (e: Exception) {
            thisLogger().warn("Error updating panel", e)
            panel.add(JLabel(message("table.loading.error", e.message ?: "Unknown error")), BorderLayout.CENTER)
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

    private var hasShownInitialEnvironmentDetails = false

    private val driverListener = object : DriverRfsListener {
        override fun driverRefreshFinished(status: DriverConnectionStatus) {
            invokeLater {
                updateMainPanel(status.getException())

                if (status == ConnectedConnectionStatus) {
                    installToolbarIfNeeded()
                    populateEnvironmentSelector()
                    selectedEnvironmentId.get()?.let { envId ->
                        driver.fileInfoManager.refreshFiles(driver.root)

                        if (!hasShownInitialEnvironmentDetails) {
                            hasShownInitialEnvironmentDetails = true
                            showEnvironmentDetails(envId)
                            (details.layout as CardLayout).show(details, ENVIRONMENT_PANEL)
                        }
                    }
                } else {
                    // Reset so environment details show on next connection
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
            // TODO: Add SchemaRegistryScopedDataManager for SR clusters/schemas (similar pattern)
            val dataManagerToProvide = when {
                selectedPath == null -> dataManager
                selectedPath.isCluster(driver) || selectedPath.isTopic -> {
                    val clusterId = selectedPath.getClusterId()
                    val envId = selectedPath.getEnvironmentId(driver)
                    if (clusterId != null && envId != null) {
                        val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
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

        // Create environment selector panel with empty model (will be populated after connection)
        val selectorPanel = panel {
            row {
                label(message("confluent.cloud.environment.selector.label"))
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

        // Create content panel with selector and tree
        val contentPanel = JPanel(BorderLayout()).apply {
            add(selectorPanel, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }

        // Wrap in SimpleToolWindowPanel to support toolbar (added after connection)
        treePanel = SimpleToolWindowPanel(true, true)
        treePanel.setContent(contentPanel)

        return treePanel
    }

    private fun installToolbarIfNeeded() {
        if (toolbarInstalled) return
        toolbarInstalled = true

        // Create Producer/Consumer toolbar (same as native Kafka connections)
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

            dataManager.preInitializeCachesForEnvironment(firstEnv.id)
        }
    }

    private fun refreshTreeForEnvironment(envId: String) {
        driver.selectedEnvironmentId = envId
        myTree.clearSelection()

        dataManager.preInitializeCachesForEnvironment(envId)

        driver.fileInfoManager.refreshFiles(driver.root)

        // Show environment details when switching environments
        invokeLater {
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
                // Show environment details if an environment is selected
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
                ?: throw IllegalStateException("Could not determine environment")
            val clusterId = rfsPath.getClusterId()
                ?: throw IllegalStateException("Could not determine cluster")

            val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
                ?: throw IllegalStateException("Cluster not found")

            val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)
            val topicsController = TopicsController(project, clusterDataManager, this)
            Disposer.register(this, topicsController)

            topicsController.getComponent()
        }
    }

    private fun showTopicDetail(rfsPath: RfsPath) {
        topicDetailPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId(driver)
                ?: throw IllegalStateException("Could not determine environment")
            val clusterId = rfsPath.getClusterId()
                ?: throw IllegalStateException("Could not determine cluster")
            val topicName = rfsPath.name

            val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
                ?: throw IllegalStateException("Cluster $clusterId not found")

            val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)

            val detailsController = TopicDetailsController(project, clusterDataManager)
            Disposer.register(this, detailsController)

            detailsController.setDetailsId(topicName)

            topicDetailPanel.add(detailsController.getComponent(), BorderLayout.CENTER)

        } catch (e: Exception) {
            topicDetailPanel.add(JLabel("Error loading topic details: ${e.message}"), BorderLayout.CENTER)
        }

        topicDetailPanel.revalidate()
        topicDetailPanel.repaint()
    }

    private fun showSchemasDetails(rfsPath: RfsPath) {
        updatePanel(schemasDetailsPanel) {
            val envId = rfsPath.getEnvironmentId(driver)
                ?: throw IllegalStateException("Could not determine environment")

            val cluster = dataManager.getKafkaClusters(envId).firstOrNull()
                ?: throw IllegalStateException("No clusters found in environment")

            val cache = dataManager.getDataPlaneCache(cluster)
            if (!cache.hasSchemaRegistry()) {
                throw IllegalStateException("No Schema Registry available")
            }

            val schemas = cache.getSchemas()
            val data = schemas.map { schema ->
                arrayOf<Any>(schema.name)
            }.toTypedArray()

            val table = JBTable(DefaultTableModel(data, arrayOf("Schema Subject"))).apply {
                setDefaultEditor(Any::class.java, null)
            }

            JBScrollPane(table)
        }
    }

    private fun showSchemaDetail(rfsPath: RfsPath) {
        schemaDetailPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId(driver) ?: run {
                schemaDetailPanel.add(JLabel("Error: Could not determine environment"), BorderLayout.CENTER)
                schemaDetailPanel.revalidate()
                schemaDetailPanel.repaint()
                return
            }

            val srId = rfsPath.getSchemaRegistryId() ?: run {
                schemaDetailPanel.add(JLabel("Error: Could not determine schema registry"), BorderLayout.CENTER)
                schemaDetailPanel.revalidate()
                schemaDetailPanel.repaint()
                return
            }

            val subjectName = rfsPath.name

            val schemaRegistry = dataManager.getSchemaRegistry(envId)?.takeIf { it.id == srId }
            if (schemaRegistry == null) {
                schemaDetailPanel.add(JLabel("Error: Schema Registry not found"), BorderLayout.CENTER)
                schemaDetailPanel.revalidate()
                schemaDetailPanel.repaint()
                return
            }

            val detailsText = buildString {
                appendLine("Schema Subject: $subjectName")
                appendLine()
                appendLine("Schema Registry: ${schemaRegistry.displayName}")
                appendLine("Schema Registry ID: ${schemaRegistry.id}")
                appendLine()
                appendLine("Click to view schema details (coming soon)")
            }

            val detailsLabel = JLabel("<html>${detailsText.replace("\n", "<br/>")}</html>").apply {
                border = JBUI.Borders.empty(10)
            }

            schemaDetailPanel.add(detailsLabel, BorderLayout.CENTER)

        } catch (e: Exception) {
            schemaDetailPanel.add(JLabel("Error loading schema details: ${e.message}"), BorderLayout.CENTER)
        }

        schemaDetailPanel.revalidate()
        schemaDetailPanel.repaint()
    }

    override fun open(rfsPath: RfsPath) = RfsUtil.select(driver.getExternalId(), rfsPath, myTree)

    override fun getComponent(): JComponent = component

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
