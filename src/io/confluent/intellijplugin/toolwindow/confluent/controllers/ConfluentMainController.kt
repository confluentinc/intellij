package io.confluent.intellijplugin.toolwindow.confluent.controllers

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
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.util.ui.tree.TreeModelAdapter
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isCluster
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isEnvironment
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistry
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isTopicsFolder
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isTopic
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemasFolder
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchema
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getClusterId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getEnvironmentId
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.getSchemaRegistryId
import io.confluent.intellijplugin.core.monitoring.toolwindow.ComponentController
import io.confluent.intellijplugin.core.monitoring.toolwindow.MainTreeController
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
) : ComponentController {

    private val dataManager = driver.dataManager

    private val detailsLayout = CardLayout()
    private val details = JPanel(detailsLayout)
    private val isNormalView = AtomicBooleanProperty(true)
    private val isErrorView = isNormalView.not()
    
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
    private val clusterDetailsPanel = createPlaceholderPanel("Cluster details panel - coming soon")
    private val schemaRegistryDetailsPanel = createPlaceholderPanel("Schema Registry details panel - coming soon")

    private val topicsDetailsPanel = JPanel(BorderLayout())

    private val topicDetailPanel = JPanel(BorderLayout())

    private val schemasDetailsPanel = JPanel(BorderLayout())

    private val schemaDetailPanel = JPanel(BorderLayout())

    private val driverListener = object : DriverRfsListener {
        override fun driverRefreshFinished(status: DriverConnectionStatus) {
            invokeLater {
                updateMainPanel(status.getException())
            }
        }
    }

    companion object {
        private const val EMPTY_PANEL = "empty"
        private const val CLUSTER_PANEL = "cluster"
        private const val SCHEMA_REGISTRY_PANEL = "schemaRegistry"
        private const val TOPICS_PANEL = "topics"
        private const val TOPIC_DETAIL_PANEL = "topicDetail"
        private const val SCHEMAS_PANEL = "schemas"
        private const val SCHEMA_DETAIL_PANEL = "schemaDetail"
    }

    override fun dispose() {}

    fun init() {
        // Add detail panels to the CardLayout
        details.add(emptyDetailsPanel, EMPTY_PANEL)
        details.add(clusterDetailsPanel, CLUSTER_PANEL)
        details.add(schemaRegistryDetailsPanel, SCHEMA_REGISTRY_PANEL)
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
        return SimpleToolWindowPanel(false, false).apply {
            add(scroll, BorderLayout.CENTER)
        }
    }

    private fun showDetailsComponent(rfsPath: RfsPath?) {
        val layout = details.layout as CardLayout
        when {
            rfsPath == null -> layout.show(details, EMPTY_PANEL)
            rfsPath.isCluster -> layout.show(details, CLUSTER_PANEL)
            rfsPath.isSchemaRegistry -> layout.show(details, SCHEMA_REGISTRY_PANEL)
            rfsPath.isTopicsFolder -> {
                showTopicsDetails(rfsPath)
                layout.show(details, TOPICS_PANEL)
            }
            rfsPath.isTopic -> {
                showTopicDetail(rfsPath)
                layout.show(details, TOPIC_DETAIL_PANEL)
            }
            rfsPath.isSchemasFolder -> {
                showSchemasDetails(rfsPath)
                layout.show(details, SCHEMAS_PANEL)
            }
            rfsPath.isSchema -> {
                showSchemaDetail(rfsPath)
                layout.show(details, SCHEMA_DETAIL_PANEL)
            }
            rfsPath.isEnvironment -> layout.show(details, EMPTY_PANEL)
            else -> layout.show(details, EMPTY_PANEL)
        }
    }

    private fun showTopicsDetails(rfsPath: RfsPath) {
        topicsDetailsPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId() ?: run {
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

            // Get cluster object
            val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
            if (cluster == null) {
                topicsDetailsPanel.add(JLabel("Error: Cluster not found"), BorderLayout.CENTER)
                topicsDetailsPanel.revalidate()
                topicsDetailsPanel.repaint()
                return
            }

            // Get data plane cache and fetch topics
            val cache = dataManager.getDataPlaneCache(cluster)
            val topics = cache.refreshTopics()

            // Create table
            val columnNames = arrayOf("", "Topic Name", "Partitions", "Replication Factor")
            val data = topics.map { topic ->
                arrayOf(
                    "",  // Empty column for future favorite icon
                    topic.topicName,
                    topic.partitionsCount.toString(),
                    topic.replicationFactor.toString()
                )
            }.toTypedArray()

            val tableModel = DefaultTableModel(data, columnNames)
            val table = JBTable(tableModel).apply {
                setDefaultEditor(Any::class.java, null)  // Make table read-only
            }

            val scrollPane = JBScrollPane(table)
            topicsDetailsPanel.add(scrollPane, BorderLayout.CENTER)

            // Add info label at the top
            val infoLabel = JLabel("${topics.size} topic(s)").apply {
                border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
            }
            topicsDetailsPanel.add(infoLabel, BorderLayout.NORTH)

        } catch (e: Exception) {
            topicsDetailsPanel.add(JLabel("Error loading topics: ${e.message}"), BorderLayout.CENTER)
        }

        topicsDetailsPanel.revalidate()
        topicsDetailsPanel.repaint()
    }

    private fun showTopicDetail(rfsPath: RfsPath) {
        topicDetailPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId() ?: run {
                topicDetailPanel.add(JLabel("Error: Could not determine environment"), BorderLayout.CENTER)
                topicDetailPanel.revalidate()
                topicDetailPanel.repaint()
                return
            }

            val clusterId = rfsPath.getClusterId() ?: run {
                topicDetailPanel.add(JLabel("Error: Could not determine cluster"), BorderLayout.CENTER)
                topicDetailPanel.revalidate()
                topicDetailPanel.repaint()
                return
            }

            val topicName = rfsPath.name

            // Get cluster object
            val cluster = dataManager.getKafkaClusters(envId).find { it.id == clusterId }
            if (cluster == null) {
                topicDetailPanel.add(JLabel("Error: Cluster not found"), BorderLayout.CENTER)
                topicDetailPanel.revalidate()
                topicDetailPanel.repaint()
                return
            }

            // Get data plane cache and fetch topics
            val cache = dataManager.getDataPlaneCache(cluster)
            val topics = cache.refreshTopics()
            val topic = topics.find { it.topicName == topicName }

            if (topic == null) {
                topicDetailPanel.add(JLabel("Topic not found: $topicName"), BorderLayout.CENTER)
                topicDetailPanel.revalidate()
                topicDetailPanel.repaint()
                return
            }

            // Create details panel
            val detailsText = buildString {
                appendLine("Topic: ${topic.topicName}")
                appendLine()
                appendLine("Partitions: ${topic.partitionsCount}")
                appendLine("Replication Factor: ${topic.replicationFactor}")
                appendLine("Internal: ${topic.isInternal}")
                appendLine()
                appendLine("Cluster: ${cluster.displayName}")
                appendLine("Cluster ID: ${cluster.id}")
            }

            val detailsLabel = JLabel("<html>${detailsText.replace("\n", "<br/>")}</html>").apply {
                border = JBUI.Borders.empty(10)
            }

            topicDetailPanel.add(detailsLabel, BorderLayout.CENTER)

        } catch (e: Exception) {
            topicDetailPanel.add(JLabel("Error loading topic details: ${e.message}"), BorderLayout.CENTER)
        }

        topicDetailPanel.revalidate()
        topicDetailPanel.repaint()
    }

    private fun showSchemasDetails(rfsPath: RfsPath) {
        schemasDetailsPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId() ?: run {
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

            // Schema Registry is per-environment, so get any cluster from this environment
            // to access the data plane cache (which has SR associated)
            val clusters = dataManager.getKafkaClusters(envId)
            val cluster = clusters.firstOrNull()

            if (cluster == null) {
                schemasDetailsPanel.add(JLabel("Error: No clusters found in environment"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            // Get data plane cache and fetch subjects
            val cache = dataManager.getDataPlaneCache(cluster)

            if (!cache.hasSchemaRegistry()) {
                schemasDetailsPanel.add(JLabel("Error: No Schema Registry available"), BorderLayout.CENTER)
                schemasDetailsPanel.revalidate()
                schemasDetailsPanel.repaint()
                return
            }

            val subjects = cache.refreshSubjects()

            // Create table
            val columnNames = arrayOf("Schema Subject")
            val data = subjects.map { subject ->
                arrayOf<Any>(subject.name)
            }.toTypedArray()

            val tableModel = DefaultTableModel(data, columnNames)
            val table = JBTable(tableModel).apply {
                setDefaultEditor(Any::class.java, null)  // Make table read-only
            }

            val scrollPane = JBScrollPane(table)
            schemasDetailsPanel.add(scrollPane, BorderLayout.CENTER)

            // Add info label at the top
            val infoLabel = JLabel("${subjects.size} schema(s)").apply {
                border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
            }
            schemasDetailsPanel.add(infoLabel, BorderLayout.NORTH)

        } catch (e: Exception) {
            schemasDetailsPanel.add(JLabel("Error loading schemas: ${e.message}"), BorderLayout.CENTER)
        }

        schemasDetailsPanel.revalidate()
        schemasDetailsPanel.repaint()
    }

    private fun showSchemaDetail(rfsPath: RfsPath) {
        schemaDetailPanel.removeAll()

        try {
            val envId = rfsPath.getEnvironmentId() ?: run {
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

            // Get schema registry object
            val schemaRegistry = dataManager.getSchemaRegistry(envId).find { it.id == srId }
            if (schemaRegistry == null) {
                schemaDetailPanel.add(JLabel("Error: Schema Registry not found"), BorderLayout.CENTER)
                schemaDetailPanel.revalidate()
                schemaDetailPanel.repaint()
                return
            }

            // Create details panel - showing basic info for now
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

    fun open(rfsPath: RfsPath) = RfsUtil.select(driver.getExternalId(), rfsPath, myTree)

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
