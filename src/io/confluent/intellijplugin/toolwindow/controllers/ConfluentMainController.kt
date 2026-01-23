package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.util.Disposer
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.OnePixelSplitter
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
import io.confluent.intellijplugin.rfs.ConfluentDriver
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
import io.confluent.intellijplugin.data.ClusterScopedDataManager
import io.confluent.intellijplugin.toolwindow.NavigableController
import io.confluent.intellijplugin.toolwindow.controllers.TopicsController
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
 * Main controller for Confluent Cloud tool window.
 * Uses core RFS infrastructure for tree management.
 *
 * Note: Does NOT extend MainTreeController because we need to pass the driver directly
 * (not through DriverManager, since Confluent connections are dynamically created).
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

    private val emptyDetailsPanel = createPlaceholderPanel("Select a cluster or schema registry to view details")
    private val loadingDetailsPanel = createPlaceholderPanel("Loading...")
    private val topicsDetailsPanel = JPanel(BorderLayout())

    private val topicDetailPanel = JPanel(BorderLayout())

    private val schemasDetailsPanel = JPanel(BorderLayout())

    private val schemaDetailPanel = JPanel(BorderLayout())

    private val driverListener = object : DriverRfsListener {
        override fun driverRefreshFinished(status: DriverConnectionStatus) {
            invokeLater {
                updateMainPanel(status.getException())

                if (status == ConnectedConnectionStatus) {
                    populateEnvironmentSelector()
                    if (myTree.selectionPath == null) {
                        selectDefaultPath()
                    }
                }
            }
        }
    }

    companion object {
        private const val EMPTY_PANEL = "empty"
        private const val LOADING_PANEL = "loading"
        private const val TOPICS_PANEL = "topics"
        private const val TOPIC_DETAIL_PANEL = "topicDetail"
        private const val SCHEMAS_PANEL = "schemas"
        private const val SCHEMA_DETAIL_PANEL = "schemaDetail"
    }

    override fun dispose() {}

    fun init() {
        details.add(emptyDetailsPanel, EMPTY_PANEL)
        details.add(loadingDetailsPanel, LOADING_PANEL)
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
            sink[MainTreeController.DATA_MANAGER] = dataManager
            sink[MainTreeController.RFS_PATH] = myTree.selectionPath?.lastDriverNode?.rfsPath
        }

        driver.addListener(driverListener)
        Disposer.register(this) { driver.removeListener(driverListener) }
        updateMainPanel(dataManager.client.connectionError)

        // TODO: Add Confluent-specific toolbar actions when needed

        treeModel.addTreeModelListener(object : TreeModelAdapter() {
            override fun process(event: TreeModelEvent, type: EventType) {
                if (myTree.selectionPath == null) {
                    runInEdt {
                        selectDefaultPath()
                    }
                }
            }
        })
    }

    private fun createNormalPanel(): OnePixelSplitter {
        treeModel = driver.createTreeModel(driver.root, project)
        val asyncTreeModel = AsyncTreeModel(treeModel, this)

        myTree = ProjectViewTree(asyncTreeModel)
        myTree.showsRootHandles = true
        myTree.isRootVisible = false
        DriverRfsTreeModel.fixInitFirstConnection(asyncTreeModel, myTree)

        RfsPaneSpeedSearch.installOn(myTree)
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
            if (rfsPath == null) {
                invokeLater {
                    if (::lastSelectedPath.isInitialized) {
                        val latestRfsPath = lastSelectedPath.lastDriverNode?.rfsPath
                        if (latestRfsPath != null && treeModel.getTreePath(latestRfsPath) != null)
                            myTree.selectionPath = lastSelectedPath
                        else selectDefaultPath()
                    }
                }
            }
            showDetailsComponent(rfsPath)
        }

        return OnePixelSplitter().apply {
            proportion = 0.25f
            dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_PROPORTION
            firstComponent = createTreePanel()
            secondComponent = details
        }
    }

    private fun createTreePanel(): SimpleToolWindowPanel {
        val scroll = JBScrollPane(myTree).apply {
            border = IdeBorderFactory.createBorder(SideBorder.LEFT)
        }

        // Create environment selector panel with empty model (will be populated after connection)
        val selectorPanel = panel {
            row {
                label("Environment:")
                comboBox(environmentComboBoxModel)
                    .also {                     comboBoxComponent ->
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

        return SimpleToolWindowPanel(false, false).apply {
            add(selectorPanel, BorderLayout.NORTH)
            add(scroll, BorderLayout.CENTER)
        }
    }

    private fun populateEnvironmentSelector() {
        val environments = dataManager.client.getEnvironments()

        environmentComboBoxModel.removeAllElements()

        environments.forEach { env ->
            environmentComboBoxModel.addElement(EnvironmentItem(env.id, env.displayName ?: env.id))
        }

        if (environments.isNotEmpty() && selectedEnvironmentId.get() == null) {
            val firstEnv = environments.first()
            driver.selectedEnvironmentId = firstEnv.id
            selectedEnvironmentId.set(firstEnv.id)
            environmentComboBoxModel.selectedItem = environmentComboBoxModel.getElementAt(0)
        }
    }

    private fun refreshTreeForEnvironment(envId: String) {
        (details.layout as CardLayout).show(details, LOADING_PANEL)
        driver.selectedEnvironmentId = envId
        myTree.clearSelection()
        driver.fileInfoManager.refreshFiles(driver.root)

        invokeLater {
            selectDefaultPath()
        }
    }

    private data class EnvironmentItem(val id: String, val displayName: String) {
        override fun toString() = displayName
    }

    private fun showDetailsComponent(rfsPath: RfsPath?) {
        val layout = details.layout as CardLayout
        when {
            rfsPath == null -> layout.show(details, EMPTY_PANEL)
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

    private fun showTopicsDetails(rfsPath: RfsPath) {
        topicsDetailsPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId(driver) ?: run {
                topicsDetailsPanel.add(JLabel("Error: Could not determine environment"), BorderLayout.CENTER)
                topicsDetailsPanel.revalidate()
                topicsDetailsPanel.repaint()
                return
            }

            val clusterId = rfsPath.getClusterId() ?: run {
                topicsDetailsPanel.add(JLabel("Error: Could not determine cluster"), BorderLayout.CENTER)
                topicsDetailsPanel.revalidate()
                topicsDetailsPanel.repaint()
                return
            }

            val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
            if (cluster == null) {
                topicsDetailsPanel.add(JLabel("Error: Cluster not found"), BorderLayout.CENTER)
                topicsDetailsPanel.revalidate()
                topicsDetailsPanel.repaint()
                return
            }

            val clusterDataManager = dataManager.getOrCreateClusterDataManager(cluster)
            val topicsController = TopicsController(project, clusterDataManager, this)
            Disposer.register(this, topicsController)

            topicsDetailsPanel.add(topicsController.getComponent(), BorderLayout.CENTER)
            topicsDetailsPanel.revalidate()
            topicsDetailsPanel.repaint()

        } catch (e: Exception) {
            topicsDetailsPanel.add(JLabel("Error loading topics: ${e.message}"), BorderLayout.CENTER)
            topicsDetailsPanel.revalidate()
            topicsDetailsPanel.repaint()
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
        schemasDetailsPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId(driver) ?: run {
                schemasDetailsPanel.add(JLabel("Error: Could not determine environment"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            val srId = rfsPath.getSchemaRegistryId() ?: run {
                schemasDetailsPanel.add(JLabel("Error: Could not determine schema registry"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            val clusters = dataManager.getKafkaClusters(envId)
            val cluster = clusters.firstOrNull()

            if (cluster == null) {
                schemasDetailsPanel.add(JLabel("Error: No clusters found in environment"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            val cache = dataManager.getDataPlaneCache(cluster)

            if (!cache.hasSchemaRegistry()) {
                schemasDetailsPanel.add(JLabel("Error: No Schema Registry available"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            val subjects = cache.refreshSubjects()

            val columnNames = arrayOf("Schema Subject")
            val data = subjects.map { subject ->
                arrayOf<Any>(subject.name)
            }.toTypedArray()

            val tableModel = DefaultTableModel(data, columnNames)
            val table = JBTable(tableModel).apply {
                setDefaultEditor(Any::class.java, null)
            }

            val scrollPane = JBScrollPane(table)
            schemasDetailsPanel.add(scrollPane, BorderLayout.CENTER)

        } catch (e: Exception) {
            schemasDetailsPanel.add(JLabel("Error loading schemas: ${e.message}"), BorderLayout.CENTER)
        }

        schemasDetailsPanel.revalidate()
        schemasDetailsPanel.repaint()
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

    private fun selectDefaultPath() {
        val envs = dataManager.client.getEnvironments()
        if (envs.isNotEmpty()) {
            val firstEnvPath = RfsPath(listOf(envs.first().id), true)
            open(firstEnvPath)
        }
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
