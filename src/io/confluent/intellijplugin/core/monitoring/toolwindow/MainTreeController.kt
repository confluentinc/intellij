package io.confluent.intellijplugin.core.monitoring.toolwindow

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.util.not
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
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
import io.confluent.intellijplugin.core.monitoring.data.MonitoringDataManager
import io.confluent.intellijplugin.core.monitoring.rfs.MonitoringDriver
import io.confluent.intellijplugin.core.rfs.driver.DriverConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsEditorErrorPanel
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsNodeAnimator
import io.confluent.intellijplugin.core.rfs.fileInfo.DriverRfsListener
import io.confluent.intellijplugin.core.rfs.projectview.pane.RfsPaneSpeedSearch
import io.confluent.intellijplugin.core.rfs.tree.DriverRfsTreeModel
import io.confluent.intellijplugin.core.rfs.util.RfsUtil
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.util.invokeLater
import io.confluent.intellijplugin.toolwindow.NavigableController
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath

abstract class MainTreeController<CONN_TYPE : ConnectionData, DRIVER_TYPE : MonitoringDriver>(
    val project: Project,
    connectionData: CONN_TYPE
) : ComponentController, NavigableController {
    @Suppress("UNCHECKED_CAST")
    protected val driver = DriverManager.getDriverById(project, connectionData.innerId) as? DRIVER_TYPE
        ?: error("Data Manager is not initialized")

    abstract val dataManager: MonitoringDataManager

    protected val detailsLayout = CardLayout()
    protected val details = JPanel(detailsLayout)
    private val isNormalView = AtomicBooleanProperty(true)
    private val isErrorView = isNormalView.not()
    protected lateinit var myTree: ProjectViewTree
    protected lateinit var treeModel: DriverRfsTreeModel

    private lateinit var normalPanel: OnePixelSplitter

    private var prevError: Throwable? = null
    private val errorPanel = JPanel(BorderLayout())

    protected lateinit var panel: DialogPanel
    private lateinit var component: JComponent

    private lateinit var lastSelectedPath: TreePath

    private val driverListener = object : DriverRfsListener {
        override fun driverRefreshFinished(status: DriverConnectionStatus) {
            invokeLater {
                updateMainPanel(status.getException())
            }
        }
    }

    override fun dispose() {}

    fun init() {
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
            sink[DATA_MANAGER] = dataManager
            sink[RFS_PATH] = myTree.selectionPath?.lastDriverNode?.rfsPath
        }

        driver.addListener(driverListener)
        Disposer.register(this) { driver.removeListener(driverListener) }
        updateMainPanel(driver.dataManager.client.connectionError)

        createToolbar()?.let {
            it.targetComponent = panel
            (normalPanel.firstComponent as SimpleToolWindowPanel).toolbar = it.component
        }

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

    protected abstract fun createToolbar(): ActionToolbar?

    protected abstract fun setupDriverSpecificTreeInit()

    protected abstract fun showDetailsComponent(rfsPath: RfsPath?)

    /** Should be called only after init() */
    override fun getComponent(): JComponent = component

    override fun open(rfsPath: RfsPath) = RfsUtil.select(driver.getExternalId(), rfsPath, myTree)

    protected open fun createNormalPanel(): OnePixelSplitter {
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
                    val latestRfsPath = lastSelectedPath.lastDriverNode?.rfsPath
                    if (latestRfsPath != null && treeModel.getTreePath(latestRfsPath) != null)
                        myTree.selectionPath = lastSelectedPath
                    else selectDefaultPath()
                }
            }
            showDetailsComponent(rfsPath)
        }

        setupDriverSpecificTreeInit()

        return OnePixelSplitter().apply {
            proportion = 0.2f
            dividerPositionStrategy = Splitter.DividerPositionStrategy.KEEP_PROPORTION
            firstComponent = createTreePanel()
            secondComponent = details
        }
    }

    /** Left panel with tree view. */
    protected open fun createTreePanel(): SimpleToolWindowPanel {
        val scroll = JBScrollPane(myTree).apply {
            border = IdeBorderFactory.createBorder(SideBorder.LEFT)
        }

        return SimpleToolWindowPanel(false, false).apply {
            add(scroll, BorderLayout.CENTER)
        }
    }

    abstract fun selectDefaultPath()

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

    companion object {
        val DATA_MANAGER: DataKey<MonitoringDataManager> = DataKey.create("kafka.data.manager")
        val RFS_PATH: DataKey<RfsPath> = DataKey.create("bdt.rfs.path")

        val AnActionEvent.dataManager
            get() = dataContext.getData(DATA_MANAGER)

        val AnActionEvent.rfsPath
            get() = dataContext.getData(RFS_PATH)
    }
}