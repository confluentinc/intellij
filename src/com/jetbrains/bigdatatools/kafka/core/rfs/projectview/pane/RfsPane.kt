package com.jetbrains.bigdatatools.kafka.core.rfs.projectview.pane

import com.intellij.ide.SelectInTarget
import com.intellij.ide.dnd.DnDManager
import com.intellij.ide.projectView.impl.AbstractProjectViewPane
import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.PopupHandler
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.ui.DefaultTreeUI
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.RfsCopyPasteSupport
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer.RfsNodeAnimator
import com.jetbrains.bigdatatools.kafka.core.rfs.icons.RfsIcons
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.RfsPanelClickLinksListener
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.RfsPanelDoubleClickListener
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.actions.RfsActionPlaces
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.actions.RfsPaneOwner
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.dnd.RfsPaneDnDTarget
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.dnd.RfsPaneDndSource
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.CompoundRfsTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.DriverRfsTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.util.RfsUtil
import com.jetbrains.bigdatatools.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionSettingProviderEP
import com.jetbrains.bigdatatools.kafka.core.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.kafka.core.util.ConnectionUtil
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.util.function.Function
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.ToolTipManager
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath

class RfsPane(
  val project: Project,
  private val rootProvider: () -> List<Pair<String, RfsPath?>>,
  private val additionalDrivers: List<Driver> = emptyList(),
  val paneId: String = PANE_ID,
  private val preprocessChildren: (List<RfsTreeNode>) -> List<RfsTreeNode> = { it },
  groups: List<ConnectionGroup> = ConnectionSettingProviderEP.getGroups(),
) : AbstractProjectViewPane(project) {

  lateinit var treeModel: CompoundRfsTreeModel
  private lateinit var asyncTreeModel: AsyncTreeModel

  private lateinit var nodeAnimator: RfsNodeAnimator

  private val treeEmptyState: JPanel by lazy {
    RfsPaneEmptyState.createPanel(project, groups)
  }

  lateinit var actionsOwner: RfsTreePaneOwner
  private lateinit var copyPasteSupport: RfsCopyPasteSupport
  lateinit var scrollPane: JScrollPane

  fun init() {
    initTree()
    initAdditional()
  }

  fun installDoubleClickListener() {
    RfsPanelDoubleClickListener.installOn(project, myTree)
  }

  private fun initAdditional() {
    myTree.setUI(RfsPaneTreeUI.prepareUiForPane(this.actionsOwner))

    RfsPanelClickLinksListener(myTree).installOn(myTree)

    treeModel.addTreeModelListener(object : TreeModelListener {
      override fun treeNodesChanged(e: TreeModelEvent?) = check()
      override fun treeNodesInserted(e: TreeModelEvent?) = check()
      override fun treeNodesRemoved(e: TreeModelEvent?) = check()
      override fun treeStructureChanged(e: TreeModelEvent?) = check()

      private fun check() = runInEdt {
        if (treeModel.root.children.isEmpty()) { //    treeEmptyState
          if (scrollPane.viewport?.view == myTree) {
            scrollPane.setViewportView(treeEmptyState)
          }
        }
        else {
          if (scrollPane.viewport?.view != myTree) {
            scrollPane.setViewportView(myTree)
          }
        }
      }
    })

    DriverRfsTreeModel.fixInitFirstConnection(asyncTreeModel, myTree)

    enableDnD()
    RfsPaneSpeedSearch.installOn(myTree)

    TreeUtil.installActions(myTree)
    ToolTipManager.sharedInstance().registerComponent(myTree)

    val shortcut = KeymapUtil.getShortcutText(IdeActions.ACTION_NEW_ELEMENT)

    myTree.emptyText.text = if (shortcut.isNotEmpty()) KafkaMessagesBundle.message("tree.empty.text.shortcut", shortcut)
    else KafkaMessagesBundle.message("tree.empty.text.menu")

    val enterAction = DumbAwareAction.create(KafkaMessagesBundle.message("action.open.tree.element")) {
      val selectionPath = myTree.selectionPath ?: return@create
      if (ConnectionUtil.enableConnection(project, selectionPath)) {
        return@create
      }

      val node = selectionPath.lastDriverNode ?: return@create
      if (node.rfsPath.isRoot && node.isAlwaysLeaf && node.onDoubleClick()) {
        return@create
      }

      if (tree.isExpanded(selectionPath)) tree.collapsePath(selectionPath)
      else tree.expandPath(selectionPath)
    }

    enterAction.registerCustomShortcutSet(CommonShortcuts.ENTER, myTree)
  }

  override fun updateFromRoot(restoreExpandedPaths: Boolean): ActionCallback {
    invokeAndWaitIfNeeded {
      treeModel.updateRoots()
    }
    return ActionCallback.DONE
  }

  override fun select(element: Any?, file: VirtualFile?, requestFocus: Boolean) {
    val fileInfo = element as? FileInfo ?: return
    RfsUtil.select(fileInfo.driver.getExternalId(), fileInfo.path, actionsOwner)
  }

  override fun enableDnD() {
    val dndManager = DnDManager.getInstance()
    dndManager.registerSource(RfsPaneDndSource(actionsOwner), myTree)
    dndManager.registerTarget(RfsPaneDnDTarget(actionsOwner, null, null), myTree)
  }

  @RequiresEdt
  fun updateRoots() {
    treeModel.updateRoots()
    tree.revalidate()
    tree.repaint()
  }

  override fun createSelectInTarget(): SelectInTarget = RfsSelectInTarget()
  override fun getTitle(): String = "RfsPane"
  override fun getIcon(): Icon = RfsIcons.BDT_ICON
  override fun getId(): String = paneId
  override fun createComponent() = scrollPane
  override fun getWeight(): Int = 1
  override fun getSelectionPaths(): Array<TreePath> = super.getSelectionPaths() ?: emptyArray()

  private fun initTree() {
    treeModel = CompoundRfsTreeModel(project, rootProvider, additionalDrivers, preprocessChildren = preprocessChildren).also {
      Disposer.register(this, it)
    }
    treeModel.updateRoots()
    asyncTreeModel = AsyncTreeModel(treeModel, this)

    nodeAnimator = RfsNodeAnimator(treeModel).also {
      Disposer.register(this, it)
    }

    myTree = ProjectViewTree(asyncTreeModel)
    myTree.showsRootHandles = true
    myTree.isRootVisible = false

    ClientProperty.put(myTree, DefaultTreeUI.AUTO_EXPAND_FILTER,
                       Function { node: Any? ->
                         // We will not autoexpand files (this belong to csv files with columns information).
                         (node as? DriverFileRfsTreeNode)?.fileInfo?.isFile == true
                       })

    scrollPane = createScrollPane()

    actionsOwner = RfsTreePaneOwner(this).also {
      Disposer.register(this, it)
    }

    PopupHandler.installPopupMenu(myTree, "BigDataTools.RfsPane.PopupActionGroup", RfsActionPlaces.RFS_PANE_POPUP)

    copyPasteSupport = RfsCopyPasteSupport(actionsOwner)

    nodeAnimator.setRepainter { driver, rfsPath ->
      val treePath = treeModel.getCachedDriverTreePath(driver, rfsPath) ?: return@setRepainter
      val pathBounds = tree.getPathBounds(treePath) ?: return@setRepainter
      tree.repaint(pathBounds)
    }
  }

  private fun createScrollPane(): JScrollPane {
    val tree = if (RfsConnectionDataManager.instance?.getConnections(project)?.isNotEmpty() == true) {
      myTree
    }
    else {
      treeEmptyState
    }

    return object : JBScrollPane(tree), UiDataProvider {
      override fun uiDataSnapshot(sink: DataSink) {
        sink[PlatformDataKeys.CUT_PROVIDER] = copyPasteSupport.cutProvider
        sink[PlatformDataKeys.COPY_PROVIDER] = copyPasteSupport.copyProvider
        sink[PlatformDataKeys.PASTE_PROVIDER] = copyPasteSupport.pasteProvider
        sink[RfsPaneOwner.DATA_KEY] = actionsOwner
        sink[LangDataKeys.NO_NEW_ACTION] = true
      }
    }.apply {
      setBorder(JBUI.Borders.empty())
      setViewportBorder(JBUI.Borders.empty())
    }
  }

  companion object {
    const val PANE_ID = "RfsPane"
  }
}