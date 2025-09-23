package io.confluent.kafka.core.rfs.util

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ui.ComponentUtil
import com.intellij.ui.tree.AbstractTreeNodeVisitor
import com.intellij.util.concurrency.EdtScheduler
import com.intellij.util.ui.tree.TreeUtil
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.depend.MasterDriver
import io.confluent.kafka.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.kafka.core.rfs.tree.node.*
import io.confluent.kafka.core.rfs.viewer.utils.DriverRfsTreeUtil.lastDriverNode
import org.jetbrains.concurrency.Promise
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

object RfsUtil {
  fun select(driverId: String, path: RfsPath, viewPane: RfsPaneOwner) {
    select(driverId, path, viewPane.jTree)
  }

  fun select(driverId: String, path: RfsPath, tree: JTree, alreadyExpanded: RfsPath = RfsPath(emptyList(), true)): Promise<TreePath> {
    val visitor = RfsTreeNodeVisitor(tree.model, driverId, path, alreadyExpanded)
    val promise = TreeUtil.promiseSelect(tree, visitor)
    return promise.onProcessed { selectedNode ->
      val driverFileRfsTreeNode = selectedNode?.lastDriverNode
      if (driverFileRfsTreeNode != null && visitor.matchesExactly(driverFileRfsTreeNode)) {
        // ToDo Hack to prevent horizontal scrolling which is scheduled in com.intellij.util.ui.tree.TreeUtil.scrollToVisible (BDIDE-3540)
        EdtScheduler.getInstance().schedule(100) {
          val scrollPane = ComponentUtil.getParentOfType(
            JScrollPane::class.java as Class<out JScrollPane?>, tree)
          scrollPane?.horizontalScrollBar?.value = 0
        }
      }
      else if (driverFileRfsTreeNode == null) {
        //Ignore
        //invokeLater {
        //  Messages.showInfoMessage(MessagesBundle.message("select.in.panel.not.found", path.stringRepresentation()),
        //                           MessagesBundle.message("select.in.panel.not.found.title"))
        //}
      }
      else {
        // TODO this hack should be replaced with normal awaiting despite whether update task is already initiated
        for (i in 0..100) {
          tree.model.getChildCount(driverFileRfsTreeNode)
          Thread.sleep(0, 1)
          val updatingChildrenTask = driverFileRfsTreeNode.updatingChildrenTask.get()
          if (updatingChildrenTask?.isExpired() == false) {
            updatingChildrenTask.future?.onProcessed {
              select(driverId, path, tree, driverFileRfsTreeNode.rfsPath)
            }
            break
          }
          if (driverFileRfsTreeNode.cachedChildren != null) {
            select(driverId, path, tree, driverFileRfsTreeNode.rfsPath)
            break
          }
        }
      }
    }
  }

  class RfsTreeNodeVisitor(val model: TreeModel,
                           private val driverId: String,
                           val path: RfsPath,
                           private val alreadyExpanded: RfsPath) : AbstractTreeNodeVisitor<Unit>({ }, null) {
    public override fun matches(node: AbstractTreeNode<*>, element: Unit): Boolean {
      return when {
        node !is RfsTreeNode -> false
        matchesExactly(node) -> true
        node is DriverFileRfsTreeNode && isSameDriverExpendedMax(node) -> true
        else -> false
      }
    }

    override fun contains(node: AbstractTreeNode<*>, element: Unit) = when {
      node is RfsCompoundRootNode -> true
      node !is DriverFileRfsTreeNode -> false
      node.driver.getExternalId() == driverId && path.startsWith(node.rfsPath) -> true
      isContainsDepend(node) -> true
      else -> false
    }

    fun matchesExactly(node: RfsTreeNode) = when (node) {
      is DriverFileRfsTreeNode -> node.driver.connectionData.innerId == driverId &&
                                  (node.rfsPath == path || node.rfsPath.isRoot && path.isRoot)
      is DisabledRfsTreeNode -> node.connectionData.innerId == driverId
      else -> false
    }

    private fun isSameDriverExpendedMax(node: DriverFileRfsTreeNode) =
      (node !is DriverCompoundTreeNode || !node.isCompound) &&
      node.cachedChildren == null && contains(node, Unit) &&
      (node.rfsPath.elements.size > alreadyExpanded.elements.size || alreadyExpanded.isRoot)

    private fun isContainsDepend(node: DriverFileRfsTreeNode): Boolean {
      val masterPath = (node.driver as? MasterDriver)?.getDependConnectionRfsPath(driverId) ?: return false
      return masterPath.startsWith(node.rfsPath)
    }
  }
}

fun String.withSlash(): String = this + (if (endsWith("/") || this == "") "" else "/")
fun String.withPrefixSlash(): String = "/" + this.removePrefix("/")