package io.confluent.intellijplugin.core.rfs.projectview.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.rfs.tree.node.RfsTreeNode
import io.confluent.intellijplugin.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreePath

interface RfsPaneOwner : Disposable {
  companion object {
    val DATA_KEY: DataKey<RfsPaneOwner> = DataKey.create("bdt.rfs.pane")
  }

  val project: Project
  val jTree: JTree

  fun getSelectedDriverNodes(): List<DriverFileRfsTreeNode> = getSelectionPaths().mapNotNull { it.lastDriverNode }
  fun getSelectedDriverNode(): DriverFileRfsTreeNode? = getSelectedDriverNodes().firstOrNull()
  fun getSelectedRfsNode(): RfsTreeNode? = getSelectionPaths().firstNotNullOfOrNull { it.lastPathComponent as? RfsTreeNode }
  fun getSelectionPaths(): Array<TreePath>

  fun getNodeForPath(path: RfsPath, driver: Driver): DriverFileRfsTreeNode?
  fun getComponent(): JComponent
}