package com.jetbrains.bigdatatools.kafka.core.rfs.projectview.toolwindow

//import com.jetbrains.bigdatatools.kafka.core.database.BdtDatabaseSettingsListener
import com.intellij.icons.AllIcons
import com.intellij.ide.FileSelectInContext
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.*
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.WriteExternalException
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.ui.content.ContentManager
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.bigdatatools.kafka.core.constants.BdtPlugins
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.depend.MasterSlaveConnectionRemoveListener
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer.RfsFileViewerSettings
import com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer.RfsViewerEditorProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.actions.RfsActionPlaces
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.pane.RfsPane
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.util.ToolbarUtils
import com.jetbrains.bigdatatools.kafka.core.util.executeNotOnEdt
import com.jetbrains.bigdatatools.kafka.core.util.invokeLater
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jdom.Element
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
@State(name = "BigDataToolWindowController", storages = [Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE)], getStateRequiresEdt = true)
class BigDataToolWindowController(val project: Project) : PersistentStateComponent<Element>, Disposable {
  private lateinit var contentManager: ContentManager

  private val projectPane: RfsPane = RfsPane(project, rootProvider = {
    RfsConnectionDataManager.instance?.getConnections(project)?.filter { it.sourceConnection == null }?.map { it.innerId to null }
    ?: emptyList()
  })

  private val connectionListener = BigDataToolsWindowListener(this)

  init {
    //We need to do it to guarantee that drivers listeners added before this controller listeners
    DriverManager.init(project)
    RfsConnectionDataManager.instance?.addListener(connectionListener)

    val masterConnectionListener = MasterSlaveConnectionRemoveListener()
    RfsConnectionDataManager.instance?.addListener(masterConnectionListener)
    Disposer.register(this, Disposable {
      RfsConnectionDataManager.instance?.removeListener(masterConnectionListener)
    })
  }

  override fun dispose() {
    RfsConnectionDataManager.instance?.removeListener(connectionListener)
  }

  fun setUp(toolWindow: ToolWindow) {
    toolWindow.title = BdtPlugins.calculateTitle()
    projectPane.init()
    projectPane.installDoubleClickListener()
    contentManager = toolWindow.contentManager
    contentManager.addUiDataProvider { sink ->
      sink[PlatformDataKeys.HELP_ID] = "big.data.tools.overview"
    }
    val panel = SimpleToolWindowPanel(true, true).apply {
      toolbar = createToolBar()
      setContent(projectPane.createComponent())
    }
    executeNotOnEdt {
      val locateAction = object : DumbAwareAction(KafkaMessagesBundle.message("action.scrollFromEditor.text"),
                                                  KafkaMessagesBundle.message("action.scrollFromEditor.hint"),
                                                  AllIcons.General.Locate) {
        override fun actionPerformed(e: AnActionEvent) {
          selectOpenedEditor()
        }
      }

      val collapseAction = DumbAwareAction.create(KafkaMessagesBundle.message("action.collapseAll.text"), AllIcons.Actions.Collapseall) {
        TreeUtil.collapseAll(projectPane.tree, 0)
      }.apply {
        registerCustomShortcutSet(CustomShortcutSet(*KeymapManager.getInstance().activeKeymap.getShortcuts("CollapseAll")),
                                  projectPane.tree, projectPane)
      }

      val content = contentManager.factory.createContent(panel, "", true).apply {
        isCloseable = false
      }
      val toolWindowEx = toolWindow as ToolWindowEx
      invokeAndWaitIfNeeded {
        contentManager.addContent(content)
        contentManager.setSelectedContent(content)
        toolWindowEx.setTitleActions(listOf(locateAction, collapseAction))
      }

      DriverManager.onDriversInit(project) {
        invokeLater {
          projectPane.updateRoots()
          projectPane.restoreExpandedPaths()

          RfsFileViewerSettings.getInstance().tabsInfo.forEach { tabInfo ->
            val node = projectPane.treeModel.root.children.firstOrNull { it.connId == tabInfo.driverId } as? DriverFileRfsTreeNode
                       ?: return@forEach
            RfsViewerEditorProvider.createFileViewerEditor(project, node.driver, RfsPath(tabInfo.pathElements, tabInfo.pathIsDirectory))
          }
        }
      }
    }
  }

  fun selectOpenedEditor() {
    val pane = getInstance(project)?.getMainPane() ?: return
    val selectInTarget = pane.createSelectInTarget()
    val selectedEditor = FileEditorManager.getInstance(project).selectedEditor
    var file = selectedEditor?.file ?: return
    if (file is BackedVirtualFile) {
      file = file.originFile
    }

    val context = FileSelectInContext(project, file)

    if (selectInTarget.canSelect(context)) {
      selectInTarget.selectIn(context, true)
    }
  }

  fun getMainPane(): RfsPane = projectPane

  override fun getState(): Element {
    val rootElement = Element(ROOT_ELEMENT)
    val paneElement = Element(HDFS_PANE_ELEMENT)

    paneElement.setAttribute(ATTRIBUTE_ID, projectPane.id) // do we need it?..

    runInEdt {
      try {
        projectPane.writeExternal(paneElement)
      }
      catch (_: WriteExternalException) {
      }
    }

    rootElement.addContent(paneElement)

    return rootElement
  }

  override fun loadState(state: Element) {
    val paneElement = state.getChild(HDFS_PANE_ELEMENT)
    if (paneElement == null) return

    try {
      projectPane.readExternal(paneElement)
    }
    catch (_: InvalidDataException) {
    }
    projectPane.restoreExpandedPaths()
  }

  private fun createToolBar(): JComponent {
    val toolbarActions = ActionManager.getInstance().getAction("BigDataTools.ToolWindow.ToolbarActionGroup") as ActionGroup
    return ToolbarUtils.createActionToolbar(projectPane.scrollPane, RfsActionPlaces.TOOLWINDOW_TOOLBAR, toolbarActions,
                                            true).component
  }

  fun createFileViewerEditor(connectionData: ConnectionData) {
    val node = projectPane.treeModel.root.children.firstOrNull { it.connId == connectionData.innerId } as? DriverFileRfsTreeNode
               ?: return

    RfsViewerEditorProvider.createFileViewerEditor(project, node.driver, node.rfsPath)
  }

  fun closeFileViewerEditors(connectionData: ConnectionData) {
    RfsViewerEditorProvider.closeFileViewerEditors(project, connectionData)
  }

  companion object {
    private const val ROOT_ELEMENT = "bigDataToolWindowController"
    private const val HDFS_PANE_ELEMENT = "hdfsProjectPane"
    private const val ATTRIBUTE_ID = "id"

    fun getInstance(project: Project): BigDataToolWindowController? = project.getService(BigDataToolWindowController::class.java)
  }
}