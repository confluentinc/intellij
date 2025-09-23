package io.confluent.kafka.core.rfs.projectview.pane

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.kafka.core.rfs.tree.CompoundRfsTreeModel
import io.confluent.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreePath

class RfsTreePaneOwner(val pane: RfsPane) : RfsPaneOwner {
  override val project: Project = pane.project
  override val jTree: JTree = pane.tree
  val treeModel: CompoundRfsTreeModel = pane.treeModel

  override fun dispose() {}

  override fun getSelectionPaths(): Array<TreePath> = pane.selectionPaths
  override fun getNodeForPath(path: RfsPath, driver: Driver) = treeModel.getCachedDriverTreePath(driver, path)?.lastDriverNode

  override fun getComponent(): JComponent = pane.componentToFocus
}