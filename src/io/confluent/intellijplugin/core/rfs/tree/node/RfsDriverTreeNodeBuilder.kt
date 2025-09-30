package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsChildrenPartId
import io.confluent.intellijplugin.core.rfs.fileInfo.RfsListMarker

open class RfsDriverTreeNodeBuilder {
  fun getNode(project: Project,
              driver: Driver,
              path: RfsPath,
              fileInfo: FileInfo? = null,
              parent: DriverFileRfsTreeNode? = null): DriverFileRfsTreeNode {
    val treeNode = createNode(project, path, driver)

    treeNode.fileInfo = fileInfo
    treeNode.parent = parent
    treeNode.update()


    return treeNode
  }

  fun getLoadMoreNode(treeNode: DriverFileRfsTreeNode, nextMarker: RfsListMarker, loadMoreAction: DriverRfsLoadMoreNode.() -> Unit): DriverRfsLoadMoreNode {
    val node = DriverRfsLoadMoreNode(treeNode.driver, treeNode.project, RfsChildrenPartId(treeNode.rfsPath, nextMarker), loadMoreAction)
    node.update()
    return node
  }

  fun getLoadingNode(treeNode: DriverFileRfsTreeNode): DriverRfsLoadingNode {
    val node = DriverRfsLoadingNode(treeNode.driver, treeNode.project)
    node.update()
    return node
  }

  fun createChildNode(node: DriverFileRfsTreeNode, fileInfo: FileInfo) =
    getNode(node.project ?: error("Null node project"), node.driver, fileInfo.path, fileInfo, node)


  protected open fun createNode(project: Project,
                                path: RfsPath,
                                driver: Driver) = DriverFileRfsTreeNode(project, path, driver)
}