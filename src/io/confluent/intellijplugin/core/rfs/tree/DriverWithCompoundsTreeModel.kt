package io.confluent.intellijplugin.core.rfs.tree

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.TreePathUtil
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.depend.MasterDriver
import io.confluent.intellijplugin.core.rfs.tree.node.DriverCompoundTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.RfsTreeNode
import io.confluent.intellijplugin.core.settings.ConnectionSettingsListener
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath

open class DriverWithCompoundsTreeModel(project: Project,
                                        rootPath: RfsPath,
                                        driver: Driver) : DriverRfsTreeModel(project, rootPath, driver, enableLoadMore = false) {
  val compoundsModels: MutableMap<RfsPath, CompoundTreeSource> = mutableMapOf()

  init {
    val listener = object : ConnectionSettingsListener {
      override fun onConnectionRemoved(project: Project?, removedConnectionData: ConnectionData) {
        clearConnection(project, removedConnectionData)
      }

      override fun onConnectionModified(project: Project?, connectionData: ConnectionData, modified: Collection<ModificationKey>) {
        clearConnection(project, connectionData)
      }

      private fun clearConnection(project: Project?, connection: ConnectionData) {
        if ((this@DriverWithCompoundsTreeModel.project == project || project == null) &&
            connection.sourceConnection == driver.connectionData.innerId) {
          this@DriverWithCompoundsTreeModel.compoundsModels.values.forEach {
            it.update(true)
          }
        }
      }
    }
    RfsConnectionDataManager.instance?.addListener(listener)
    @Suppress("LeakingThis")
    Disposer.register(this) {
      RfsConnectionDataManager.instance?.removeListener(listener)
    }
  }

  override fun getChildren(parent: Any?): List<RfsTreeNode>? = if (parent is DriverCompoundTreeNode && parent.isCompound)
    getCompoundChildren(parent) + (super.getChildren(parent) ?: emptyList())
  else
    super.getChildren(parent)

  private fun getCompoundChildren(parent: DriverCompoundTreeNode): List<RfsTreeNode> {
    val treeSource = compoundsModels.getOrPut((parent as DriverFileRfsTreeNode).rfsPath) {
      initChildCompoundModel(parent)
    }

    invokeAndWaitIfNeeded {
      treeSource.update()
      //parent.cachedChildren = treeSource.treeModel.root.children
    }

    return treeSource.treeModel.root.children
  }

  private fun initChildCompoundModel(parent: DriverFileRfsTreeNode): CompoundTreeSource {
    val compoundTreeSource = CompoundTreeSource(project, parent.driver as MasterDriver, parent.rfsPath)
    Disposer.register(this, compoundTreeSource)

    val childrenCompoundModel = compoundTreeSource.treeModel
    val treeListener = createChildListener(parent)

    childrenCompoundModel.addTreeModelListener(treeListener)
    Disposer.register(this, Disposable {
      childrenCompoundModel.removeTreeModelListener(treeListener)
    })
    return compoundTreeSource
  }

  private fun createChildListener(parent: DriverFileRfsTreeNode) = object : TreeModelListener {
    override fun treeNodesChanged(e: TreeModelEvent) =
      this@DriverWithCompoundsTreeModel.treeNodesChanged(convertDriverTreePath(parent, e.treePath), e.childIndices, e.children)

    override fun treeNodesInserted(e: TreeModelEvent) =
      this@DriverWithCompoundsTreeModel.treeNodesInserted(convertDriverTreePath(parent, e.treePath), e.childIndices, e.children)

    override fun treeNodesRemoved(e: TreeModelEvent) =
      this@DriverWithCompoundsTreeModel.treeNodesRemoved(convertDriverTreePath(parent, e.treePath), e.childIndices, e.children)

    override fun treeStructureChanged(e: TreeModelEvent) =
      this@DriverWithCompoundsTreeModel.treeStructureChanged(convertDriverTreePath(parent, e.treePath), e.childIndices, e.children)
  }

  private fun convertDriverTreePath(parent: DriverFileRfsTreeNode, driverPath: TreePath): TreePath? {
    val prevPath = mutableListOf<AbstractTreeNode<*>>(parent)
    var cur: AbstractTreeNode<*> = parent
    while (cur.parent != null) {
      cur = cur.parent
      prevPath.add(cur)
    }

    @Suppress("UNCHECKED_CAST") val newPath = TreePathUtil.convertTreePathToArray(driverPath).toList() as List<AbstractTreeNode<*>>
    prevPath.reverse()
    prevPath.addAll(newPath.drop(1))

    return TreePathUtil.convertArrayToTreePath(*prevPath.toTypedArray())
  }
}