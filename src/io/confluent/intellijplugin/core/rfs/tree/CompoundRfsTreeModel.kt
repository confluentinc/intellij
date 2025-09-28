package io.confluent.intellijplugin.core.rfs.tree

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.depend.MasterDriver
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.intellijplugin.core.rfs.tree.node.*
import io.confluent.intellijplugin.core.settings.manager.RfsConnectionDataManager
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.event.TreeModelEvent
import javax.swing.event.TreeModelListener
import javax.swing.tree.TreePath

class CompoundRfsTreeModel(val project: Project,
                           val getRootSources: () -> List<Pair<String, RfsPath?>>,
                           private val additionalDrivers: List<Driver> = emptyList(),
                           val preprocessChildren: (List<RfsTreeNode>) -> List<RfsTreeNode> = { it }) : BaseTreeModel<RfsTreeNode>(),
                                                                                                        InvokerSupplier {
  private val driverTreeModels = CopyOnWriteArrayList<DriverRfsTreeModel>()

  private val dependTreeCompoundSources: List<CompoundTreeSource>
    get() {
      val compoundDepend = driverTreeModels.filterIsInstance<DriverWithCompoundsTreeModel>()
      return compoundDepend.flatMap { compoundsTreeModel ->
        val values = compoundsTreeModel.compoundsModels.values
        values
      }
    }

  private val allDriversTreeModel: List<DriverRfsTreeModel>
    get() = driverTreeModels + dependTreeCompoundSources.flatMap { it.treeModel.driverTreeModels }

  private val disabledRootNodes = CopyOnWriteArrayList<DisabledRfsTreeNode>()

  private val rootNode = RfsCompoundRootNode(project) {
    driverTreeModels.map { it.root } + disabledRootNodes
  }

  override fun getRoot() = rootNode

  override fun getChildren(parent: Any?): List<RfsTreeNode>? {
    val parentNode = parent as? RfsTreeNode ?: error("Cannot cast to RfsTreeNode")
    val treeNodes = when (parentNode) {
                      rootNode -> return rootNode.children
                      is DriverRfsTreeNode -> {
                        val driverManager = allDriversTreeModel.first { it.driver == parentNode.driver }
                        driverManager.getChildren(parent)
                      }
                      is DisabledRfsTreeNode -> emptyList()
                      else -> error("Wrong type of node")
                    } ?: return null
    return preprocessChildren(treeNodes)
  }

  override fun isLeaf(`object`: Any?): Boolean {
    return when (val node = `object` as? RfsTreeNode) {
      rootNode -> rootNode.children.isEmpty()
      is DisabledRfsTreeNode -> true
      is DriverRfsTreeNode -> {
        val driverTreeModel = getDriverTreeModelForNode(node)
        driverTreeModel?.isLeaf(node) ?: true
      }
      else -> error("Unreachable code")
    }
  }

  @RequiresEdt
  fun updateRoots() {
    ThreadingAssertions.assertEventDispatchThread()
    val newRoots = getRootSources()

    removeDrivers(newRoots)
    removeDisabled(newRoots)
    addNewDriverRoots(newRoots)
    addNewDisabledNodes(newRoots)

    dependTreeCompoundSources.forEach {
      it.update()
    }
  }

  fun getUsedDriverManagers() = driverTreeModels.toList()

  fun getCachedDriverTreePath(driver: Driver, rfsPath: RfsPath): TreePath? {
    val rootDriverModel = driverTreeModels.firstOrNull { it.driver.getExternalId() == driver.getExternalId() }
    if (rootDriverModel != null) {
      val driverPath = rootDriverModel.getTreePath(rfsPath) ?: return null
      return convertDriverTreePath(driverPath)
    }

    val dependConnectionData = driver.connectionData
    val masterConnectionId = dependConnectionData.sourceConnection ?: return null
    val masterModel = driverTreeModels.firstOrNull { it.driver.getExternalId() == masterConnectionId } ?: return null
    val masterDriver = masterModel.driver
    val masterPath = (masterDriver as MasterDriver).getDependConnectionRfsPath(dependConnectionData.innerId) ?: return null
    val masterTreePath = getCachedDriverTreePath(masterDriver, masterPath) ?: return null

    val compoundTreeSource = (masterModel as DriverWithCompoundsTreeModel).compoundsModels[masterPath] ?: return null
    val slavePath = compoundTreeSource.treeModel.getCachedDriverTreePath(driver, rfsPath) ?: return null

    val nodes = TreePathUtil.convertTreePathToArray(masterTreePath).toList() + TreePathUtil.convertTreePathToArray(slavePath).toList().drop(
      1)

    return TreePathUtil.convertCollectionToTreePath(nodes)
  }


  override fun getInvoker(): Invoker = Invoker.Background.forBackgroundThreadWithoutReadAction(this)

  private fun addNewDisabledNodes(newRoots: List<Pair<String, RfsPath?>>) {
    val newDisabledConn = newRoots.mapNotNull {
      val connectionData = RfsConnectionDataManager.instance?.getConnectionById(project, it.first) ?: return@mapNotNull null
      if (connectionData.isEnabled)
        null
      else
        connectionData
    }.filterIsInstance<RemoteFsDriverProvider>()

    val newDisabledNodes = newDisabledConn.map { createDisabledNode(it) }
    val existsDisabledNodes = disabledRootNodes
    val addedNodes = newDisabledNodes - existsDisabledNodes

    if (addedNodes.isEmpty())
      return

    val prevChildrenSize = driverTreeModels.size

    disabledRootNodes += addedNodes
    disabledRootNodes.sortWith(compareBy({ it.connectionData.groupId }, { it.connectionData.name }))

    val indices = addedNodes.map { disabledRootNodes.indexOf(it) + prevChildrenSize }
    val rootTreePath = TreePath(rootNode)

    addedNodes.forEach { it.update() }
    treeNodesInserted(rootTreePath, indices.toIntArray(), addedNodes.toTypedArray())
  }

  private fun addNewDriverRoots(freshRoots: List<Pair<String, RfsPath?>>) {
    val newDriverRoots = freshRoots.mapNotNull {
      val driver = DriverManager.getDriverById(project, it.first) ?: return@mapNotNull null
      val path = it.second ?: driver.root
      driver to path
    } + additionalDrivers.map { it to it.root }

    val existsManagers = driverTreeModels
    val addRoots = newDriverRoots.filter { (driver, rfsPath) ->
      !existsManagers.any { it.driver == driver && it.root.rfsPath == rfsPath }
    }
    if (addRoots.isEmpty())
      return
    val newDriverTrees = addRoots.map { (driver, rfsPath) ->
      initDriverTreeModel(driver, rfsPath)
    }
    val prevSize = driverTreeModels.size
    driverTreeModels += newDriverTrees
    driverTreeModels.sortBy { it.driver.presentableName }
    val newIndices = newDriverTrees.map { driverTreeModels.indexOf(it) + prevSize }

    val rootTreePath = TreePath(rootNode)
    val newNodes = newDriverTrees.map { it.root }.toTypedArray()
    treeNodesInserted(rootTreePath, newIndices.toIntArray(), newNodes)
  }

  private fun removeDisabled(newRoots: List<Pair<String, RfsPath?>>) {
    val newNodes = newRoots.asSequence()
      .mapNotNull { RfsConnectionDataManager.instance?.getConnectionById(project, it.first) }
      .filterIsInstance<RemoteFsDriverProvider>()
      .filterNot { it.isEnabled }
      .map { createDisabledNode(it) }
      .toList()
    val removedNodes = disabledRootNodes - newNodes.toSet()
    val indices = removedNodes.map { disabledRootNodes.indexOf(it) }
    if (removedNodes.isEmpty())
      return

    disabledRootNodes -= removedNodes.toSet()
    treeNodesRemoved(TreePath(rootNode), indices.toIntArray(), removedNodes.toTypedArray())
  }

  private fun removeDrivers(freshRoots: List<Pair<String, RfsPath?>>) {
    val freshDriver = freshRoots.mapNotNull {
      val driver = DriverManager.getDriverById(project, it.first) ?: return@mapNotNull null
      driver to it.second
    }
    val managersForRemove = driverTreeModels.filter {
      val driver = it.driver
      val treeRootPath = it.root.rfsPath
      val driverRootPath = driver.root
      !freshDriver.any { (newDriver, newRoot) ->
        newDriver == driver && (driverRootPath == treeRootPath && newRoot == null || treeRootPath == newRoot)
      }
    }

    if (managersForRemove.isEmpty())
      return

    val indices = managersForRemove.map { driverTreeModels.indexOf(it) }
    driverTreeModels -= managersForRemove.toSet()
    val rootTreePath = TreePath(rootNode)
    treeNodesRemoved(rootTreePath, indices.toIntArray(), managersForRemove.map { it.root }.toTypedArray())
    managersForRemove.forEach {
      Disposer.dispose(it)
    }
  }


  private fun getDriverTreeModelForNode(node: RfsTreeNode): DriverRfsTreeModel? {
    if (node !is DriverFileRfsTreeNode)
      return null
    //TODO: not found depend
    return allDriversTreeModel.find { it.driver == node.driver && node.rfsPath.startsWith(it.rootPath) }
  }

  private fun initDriverTreeModel(driver: Driver,
                                  rfsPath: RfsPath): DriverRfsTreeModel {
    val treeModel = driver.createTreeModel(rfsPath, project)
    Disposer.register(this, treeModel)

    val treeListener = object : TreeModelListener {
      override fun treeNodesChanged(e: TreeModelEvent) =
        this@CompoundRfsTreeModel.treeNodesChanged(convertDriverTreePath(e.treePath), e.childIndices, e.children)

      override fun treeNodesInserted(e: TreeModelEvent) =
        this@CompoundRfsTreeModel.treeNodesInserted(convertDriverTreePath(e.treePath), e.childIndices, e.children)

      override fun treeNodesRemoved(e: TreeModelEvent) =
        this@CompoundRfsTreeModel.treeNodesRemoved(convertDriverTreePath(e.treePath), e.childIndices, e.children)

      override fun treeStructureChanged(e: TreeModelEvent) =
        this@CompoundRfsTreeModel.treeStructureChanged(convertDriverTreePath(e.treePath), e.childIndices, e.children)
    }

    treeModel.addTreeModelListener(treeListener)
    Disposer.register(this, Disposable {
      treeModel.removeTreeModelListener(treeListener)
    })

    return treeModel
  }

  private fun createDisabledNode(connectionData: RemoteFsDriverProvider) = DisabledRfsTreeNode(project, connectionData)

  private fun convertDriverTreePath(driverPath: TreePath) =
    TreePathUtil.convertArrayToTreePath(rootNode, *TreePathUtil.convertTreePathToArray(driverPath))
}


