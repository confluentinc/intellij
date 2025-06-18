package com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer

import com.intellij.openapi.project.Project
import com.intellij.ui.treeStructure.Tree
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.actions.RfsPaneOwner
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.TreePath

class RfsTablePaneOwner(private val viewer: RfsTableViewer) : RfsPaneOwner {
  override val project: Project = viewer.project
  override val jTree: JTree = Tree() // viewer.treeTable.tree

  override fun getSelectionPaths(): Array<TreePath> = viewer.table.selectedRows.map {
    val modelIndex = viewer.table.convertRowIndexToModel(it)
    val entry = viewer.dataTableModel.getEntry(modelIndex)
    val node = DriverFileRfsTreeNode(project, entry!!.rfsPath, viewer.driver).apply {
      fileInfo = entry.fileInfo
    }
    TreePath(node)
  }.toTypedArray()

  override fun getNodeForPath(path: RfsPath, driver: Driver) = DriverFileRfsTreeNode(viewer.project, path, driver)

  override fun getComponent(): JComponent = viewer.getComponent()

  override fun dispose() = Unit
}