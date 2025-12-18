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
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.util.ui.tree.TreeModelAdapter
import io.confluent.intellijplugin.rfs.ConfluentDriver
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isCluster
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isEnvironment
import io.confluent.intellijplugin.rfs.ConfluentDriver.Companion.isSchemaRegistry
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
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.TreeModelEvent
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

    private val emptyDetailsPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Select a cluster or schema registry to view details"), BorderLayout.CENTER)
    }

    private val clusterDetailsPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Cluster details panel - coming soon"), BorderLayout.CENTER)
    }

    private val schemaRegistryDetailsPanel = JPanel(BorderLayout()).apply {
        add(JLabel("Schema Registry details panel - coming soon"), BorderLayout.CENTER)
    }

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
    }

    override fun dispose() {}

    fun init() {
        // Add detail panels to the CardLayout
        details.add(emptyDetailsPanel, EMPTY_PANEL)
        details.add(clusterDetailsPanel, CLUSTER_PANEL)
        details.add(schemaRegistryDetailsPanel, SCHEMA_REGISTRY_PANEL)

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
            rfsPath.isEnvironment -> layout.show(details, EMPTY_PANEL)
            else -> layout.show(details, EMPTY_PANEL)
        }
    }

    private fun selectDefaultPath() {
        val envs = dataManager.getEnvironments()
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
