package io.confluent.kafka.core.rfs.projectview

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.util.EditSourceOnDoubleClickHandler.TreeMouseListener
import com.intellij.util.OpenSourceUtil
import com.intellij.util.ui.tree.TreeUtil
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import io.confluent.kafka.core.util.ConnectionUtil
import java.awt.event.MouseEvent
import javax.swing.JTree
import javax.swing.plaf.basic.BasicTreeUI
import javax.swing.tree.TreePath

/** Support on enabling connections / opening files on doubleclick. */
class RfsPanelDoubleClickListener private constructor(private val project: Project,
                                                      private val tree: JTree,
                                                      private val onFileDoubleClick: ((tree: JTree, node: DriverFileRfsTreeNode) -> Unit)?)
  : TreeMouseListener(tree, null) {

  private fun onDefaultFileDoubleClick(tree: JTree, node: DriverFileRfsTreeNode) {
    val parentDataContext = DataManager.getInstance().getDataContext(tree)
    val navigatable = when (val userObject = TreeUtil.getUserObject(node)) {
      is Navigatable -> userObject
      else -> node
    }
    val dataContext = SimpleDataContext.getSimpleContext(CommonDataKeys.NAVIGATABLE_ARRAY, arrayOf(navigatable), parentDataContext)
    OpenSourceUtil.openSourcesFrom(dataContext, true)
  }

  override fun onDoubleClick(e: MouseEvent): Boolean {
    val selectionPath = getPath(e, tree) ?: return false

    if (ConnectionUtil.enableConnection(project, selectionPath)) return true

    val node = selectionPath.lastDriverNode ?: return false

    if (node.onDoubleClick()) return true

    val fileInfo = node.fileInfo
    return when {
      fileInfo != null && fileInfo.isFile -> {
        if (onFileDoubleClick == null)
          onDefaultFileDoubleClick(tree, node)
        else
          onFileDoubleClick.invoke(tree, node)
        true
      }
      else -> super.onDoubleClick(e)
    }
  }

  companion object {
    fun getPath(e: MouseEvent, tree: JTree): TreePath? {
      val clickPath = if (tree.ui is BasicTreeUI)
        tree.getClosestPathForLocation(e.x, e.y)
      else
        tree.getPathForLocation(e.x, e.y)

      clickPath ?: return null

      val selectionPath = tree.selectionPath ?: return null
      if (clickPath != selectionPath) return null
      return selectionPath
    }

    fun installOn(project: Project, tree: JTree, onFileDoubleClick: ((tree: JTree, node: DriverFileRfsTreeNode) -> Unit)? = null): Unit =
      RfsPanelDoubleClickListener(project, tree, onFileDoubleClick).installOn(tree)
  }
}