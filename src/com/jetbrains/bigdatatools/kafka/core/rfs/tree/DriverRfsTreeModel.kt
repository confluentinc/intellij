package com.jetbrains.bigdatatools.kafka.core.rfs.tree

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.BaseTreeModel
import com.intellij.ui.tree.TreePathUtil
import com.intellij.util.concurrency.Invoker
import com.intellij.util.concurrency.InvokerSupplier
import com.intellij.util.ui.tree.TreeModelAdapter
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.ErrorResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.OkResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.DriverRfsListener
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsFileInfoChildren
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsListMarker
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverRfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsMetaInfoNode
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsTreeNode
import com.jetbrains.bigdatatools.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import com.jetbrains.bigdatatools.kafka.core.util.toPresentableText
import org.jetbrains.concurrency.asPromise
import javax.swing.JTree
import javax.swing.event.TreeModelEvent
import javax.swing.tree.TreePath

open class DriverRfsTreeModel(val project: Project,
                              val rootPath: RfsPath,
                              val driver: Driver,
                              private val enableLoadMore: Boolean = true) : BaseTreeModel<RfsTreeNode>(), InvokerSupplier {
  private val fileManager = driver.fileInfoManager
  private val nodeBuilder = driver.treeNodeBuilder

  private val cachedRoot: DriverFileRfsTreeNode = let {
    val nodeInfo = fileManager.getCachedFileInfoOrReload(rootPath)
    val node = this.nodeBuilder.getNode(project, driver, rootPath)
    updateNode(nodeInfo, TreePath(node))
    node
  }

  private val rfsListener = object : DriverRfsListener {
    override fun childrenLoaded(path: RfsPath, children: SafeResult<RfsFileInfoChildren>) {
      val treePath = getTreePath(path) ?: let {
        invokeTreeUpdateIfRequired(path)
        return
      }
      val node = treePath.lastDriverNode
      if (node != null) {
        addLoadedChildren(treePath, node, children)
      }
    }

    override fun fileInfoLoaded(path: RfsPath, fileInfo: SafeResult<FileInfo?>) {
      val treePath = getTreePath(path) ?: return
      updateNode(fileInfo, treePath)
    }

    override fun treeUpdated(path: RfsPath) {
      log(path, "Listener - Tree updated.")
      val treePath = getTreePath(path) ?: let {
        invokeTreeUpdateIfRequired(path)
        return
      }
      val cachedFileNode = driver.getCachedFileInfo(path)

      updateNode(cachedFileNode, treePath)
      invokeUpdateChildren(treePath, propagate = true)
    }


    override fun nodeUpdated(path: RfsPath) {
      val treePath = getTreePath(path) ?: return

      val cachedFileNode = driver.getCachedFileInfo(path)
      updateNode(cachedFileNode, treePath)
    }
  }

  private fun invokeTreeUpdateIfRequired(path: RfsPath) {
    if (!path.startsWith(rootPath)) return
    var node = root
    while (true) {
      node = node.cachedChildren?.find { path.startsWith(it.rfsPath) } ?: break
    }
    if (node.cachedChildren == null)
      return

    val treePath = getTreePath(node.rfsPath) ?: error("Unexpected error, cannot get path for existent node")
    invokeUpdateChildren(treePath, propagate = false)
  }

  init {
    @Suppress("LeakingThis")
    Disposer.register(driver, this)
    driver.addListener(rfsListener)

    rfsListener.treeUpdated(rootPath)
  }

  override fun dispose() = driver.removeListener(rfsListener)

  override fun getRoot(): DriverFileRfsTreeNode = cachedRoot

  override fun isLeaf(`object`: Any?): Boolean {
    val driverRfsTreeNode = `object` as? DriverFileRfsTreeNode ?: return super.isLeaf(`object`)

    return if (driverRfsTreeNode.error == null)
      driverRfsTreeNode.rfsPath.isFile
    else
      true
  }

  override fun getInvoker() = Invoker.forBackgroundPoolWithoutReadAction(this)

  fun getTreePath(path: RfsPath): TreePath? {
    val treeNodes = getTreePathNodeList(path) ?: return null
    return TreePathUtil.convertArrayToTreePath(*treeNodes.toTypedArray())
  }

  override fun getChildren(parent: Any?): List<RfsTreeNode>? {
    val treeNode = parent as? DriverRfsTreeNode ?: return emptyList()

    return when {
      treeNode is DriverFileRfsTreeNode && treeNode.rfsPath.isDirectory -> getFileInfoChildren(treeNode)
      treeNode is RfsMetaInfoNode -> treeNode.children
      else -> emptyList()
    }.also {
      (treeNode as? DriverFileRfsTreeNode)?.rfsPath?.let { rfsPath -> log(rfsPath, "Get children. $it") }
    }
  }

  private fun getFileInfoChildren(treeNode: DriverFileRfsTreeNode): List<DriverRfsTreeNode> {
    val rfsPath = treeNode.rfsPath
    val treePath = getTreePath(rfsPath)

    if (treePath == null) {
      invokeTreeUpdateIfRequired(rfsPath)
    }
    else if (treeNode.isInvalidated.get()) {
      treeNode.loadChildrenIfNotRunning(refresh = treeNode.cachedChildren != null,
                                        loadingNode = this.nodeBuilder.getLoadingNode(treeNode)) {
        treeNodesRemoved(treePath, intArrayOf(treeNode.children.size), null)
        treeNodesInserted(treePath, intArrayOf(treeNode.children.size), null)
        driver.listStatusAsync(rfsPath) { calculatedChildren ->
          // if result was actually loaded, addLoadedChildren will be called twice
          addLoadedChildren(treePath, treeNode, calculatedChildren)
          true
        }.asPromise()
      }
    }
    val cachedChildren = treeNode.cachedChildren ?: emptyList()
    return cachedChildren + listOfNotNull(treeNode.loadMoreNode) + listOfNotNull(treeNode.loadingNode)
  }

  private fun loadMore(treePath: TreePath, treeNode: DriverFileRfsTreeNode, nextMarker: RfsListMarker) {
    treeNode.loadChildrenIfNotRunning(refresh = false, loadingNode = this.nodeBuilder.getLoadingNode(treeNode)) {
      driver.listStatusAsync(treeNode.rfsPath, startFrom = nextMarker) {
        addLoadedChildren(treePath, treeNode, it, append = true)
        true
      }.asPromise()
    }
    treeNodesRemoved(treePath, intArrayOf(treeNode.children.size), arrayOf(treeNode.loadMoreNode))
    treeNodesInserted(treePath, intArrayOf(treeNode.children.size), arrayOf(treeNode.loadingNode))
  }

  private fun addLoadedChildren(treePath: TreePath,
                                treeNode: DriverFileRfsTreeNode,
                                calculatedChildren: SafeResult<RfsFileInfoChildren>,
                                append: Boolean = false) {
    val newChildrenFileInfos = preprocessLoadedChildren(calculatedChildren.result?.fileInfos?.toList() ?: emptyList())
    val newChildrenNodes = newChildrenFileInfos.map { this.nodeBuilder.createChildNode(treeNode, it) }

    if (calculatedChildren is ErrorResult) {
      treeNode.error = calculatedChildren.exception
    }
    val nextMarker = calculatedChildren.result?.nextMarker
    val oldChildrenNodes = treeNode.cachedChildren
    val oldLoadMoreNode = treeNode.loadMoreNode
    val loadMoreNode = if (enableLoadMore && nextMarker?.marker != null)
      oldLoadMoreNode ?: this.nodeBuilder.getLoadMoreNode(treeNode, nextMarker) { loadMore(treePath, treeNode, nextMarker) }
    else null
    val oldChildrenFileInfos = oldChildrenNodes?.map { it.fileInfo }
    if (append && calculatedChildren.result?.fromMarker != null && oldChildrenFileInfos != null) {
      treeNode.addLoadedChildren(oldChildrenNodes + newChildrenNodes, loadMoreNode, emptyList())
      treeNodesInserted(treePath, newChildrenNodes.indices.map { oldChildrenNodes.size + it }.toIntArray(), newChildrenNodes.toTypedArray())
    }
    else if (calculatedChildren.result?.fromMarker == null) {
      if (!treeNode.isInvalidated.get()) {
        val oldChildrenNodesByFileInfo = oldChildrenNodes?.mapNotNull { it.fileInfo?.to(it) }?.toMap() ?: emptyMap()
        val updatedChildrenNodes = newChildrenNodes.mapIndexed { i, it -> oldChildrenNodesByFileInfo[it.fileInfo] ?: it }
        treeNode.addLoadedChildren(updatedChildrenNodes, loadMoreNode, emptyList())
        if (newChildrenNodes != oldChildrenNodes || loadMoreNode != oldLoadMoreNode) {
          treeStructureChanged(treePath, null, null)
        }
      }
      else {
        treeNode.addLoadedChildren(newChildrenNodes, loadMoreNode, emptyList())
        treeStructureChanged(treePath, null, null)
      }
    }
    if (calculatedChildren is OkResult && calculatedChildren.result.fileInfos == null) {
      treePath.parentPath?.let { parentPath -> invokeUpdateChildren(parentPath, propagate = false) }
    }
  }

  protected open fun preprocessLoadedChildren(calculatedChildren: List<FileInfo>) = calculatedChildren

  private fun updateNode(cachedFileNode: SafeResult<FileInfo?>?, treePath: TreePath) {
    updateNode(cachedFileNode?.result, cachedFileNode?.exception, treePath)
  }

  private fun updateNode(fileInfo: FileInfo?, error: Throwable?, treePath: TreePath) {
    val node = treePath.lastDriverNode ?: return

    if (node.fileInfo == fileInfo && error?.toPresentableText() == node.error?.toPresentableText()) {
      log(node.rfsPath, "Ignore node update ${System.identityHashCode(node)}. The same. Loading: ${node.isLoading}")
      return
    }

    node.fileInfo = fileInfo
    node.error = error
    log(node.rfsPath, "Node update ${System.identityHashCode(node)}. File info: $fileInfo, error: $error")

    node.update()

    treeNodesChanged(treePath, null, null)
  }

  private fun getTreePathNodeList(rfsPath: RfsPath): List<DriverFileRfsTreeNode>? {
    val nodes = mutableListOf<DriverFileRfsTreeNode>()
    if (!rfsPath.startsWith(rootPath))
      return null

    var curNode: DriverFileRfsTreeNode? = root
    while (curNode != null && curNode.rfsPath != rfsPath) {
      nodes.add(curNode)
      curNode = curNode.cachedChildren?.find { rfsPath.startsWith(it.rfsPath) }
    }

    return if (curNode != null)
      (nodes + curNode).toList()
    else
      null
  }

  protected fun invokeUpdateChildren(treePath: TreePath, propagate: Boolean) {
    val node = treePath.lastDriverNode
    node?.isInvalidated?.set(true)
    // TODO node?.updatingChildrenTask.cancel()
    node?.updatingChildrenTask?.set(null)
    if (propagate) {
      treeStructureChanged(treePath, null, null)
    }
    else {
      if (node != null && node.isUpdatingChildren)
        return
      //Invoke reload children for async model
      treeNodesInserted(treePath, null, null)
    }
  }


  @Suppress("DuplicatedCode")
  private fun log(rfsPath: RfsPath, msg: String) {
    val path = if (rfsPath.isRoot) {
      "<Root>"
    }
    else
      rfsPath.stringRepresentation()

    val infoMsg = "$path - $msg. Driver:${driver.presentableName}"

    if (ApplicationManager.getApplication().isUnitTestMode) {
      logger.info(infoMsg)
    }
    else {
      logger.debug(infoMsg)
    }
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)

    fun fixInitFirstConnection(asyncTreeModel: AsyncTreeModel, tree: JTree) {
      //We need to do it because the root invisible node is not expand automatically on first node insert
      //so we need do it manually
      asyncTreeModel.addTreeModelListener(object : TreeModelAdapter() {
        override fun process(event: TreeModelEvent, type: EventType) {
          if (type == EventType.NodesInserted && event.path?.size == 1)
            tree.expandPath(event.treePath)
        }
      })
    }

  }
}

