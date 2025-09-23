package io.confluent.kafka.core.rfs.projectview.actions

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.driver.isLocal
import io.confluent.kafka.core.rfs.tree.node.DisabledRfsTreeNode
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode

interface RfsProjectPaneActionContext {
  val pane: RfsPaneOwner
  val project: Project
  fun getSelectedDriverNode() = pane.getSelectionPaths().firstOrNull()?.lastDriverNode
  fun getSelectedDriverNodes() = pane.getSelectionPaths().mapNotNull { it.lastDriverNode }
  fun isSingleSelect() = pane.getSelectionPaths().size == 1
  fun isDriverSelect() = pane.getSelectionPaths().map { it.lastPathComponent }.all { it is DriverFileRfsTreeNode }
  fun isSingleDriverSelect() = isSingleSelect() && isDriverSelect()
  fun isNonEmptyDriverSelect() = pane.getSelectionPaths().isNotEmpty() && isDriverSelect()
  fun isMount() = getSelectedDriverNodes().any { it.isMount }
  fun isDeleteSupport() = getSelectedDriverNodes().map { it.fileInfo }.all { it?.isActionDeleteSupport == true }
  fun isFolder() = getSelectedDriverNodes().any { it.rfsPath.isDirectory }
  fun getSelectedFileInfo() = getSelectedDriverNode()?.fileInfo
  fun getSelectedFileInfos() = getSelectedDriverNodes().mapNotNull { it.fileInfo }
  fun getSelectedDriver() = getSelectedDriverNode()?.driver
  fun isFileStorageNode() = isDriverSelect() && getSelectedDriverNodes().all { it.driver.isFileStorage }
  fun isLoaded() = getSelectedDriverNodes().all { it.fileInfo != null }
  fun isLocalDiver() = getSelectedDriverNodes().all { it.driver.isLocal() }
  fun isDisabledConnection(): Boolean {
    val paths = pane.getSelectionPaths()
    return paths.size == 1 && paths.first().lastPathComponent is DisabledRfsTreeNode
  }
  fun getDisabledConnectionNode(): DisabledRfsTreeNode? =
    pane.getSelectionPaths().map { it.lastPathComponent }.filterIsInstance<DisabledRfsTreeNode>().firstOrNull()
}
