package com.jetbrains.bigdatatools.kafka.core.rfs.projectview

import com.intellij.ui.ClickListener
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.treeStructure.Tree
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverRfsLoadMoreNode
import java.awt.event.MouseEvent
import javax.swing.JTree

/** Support for "load more" links in RFS tree. */
class RfsPanelClickLinksListener(private val tree: JTree) : ClickListener() {
  override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
    if (e.button == MouseEvent.BUTTON1) {
      val selectionPath = RfsPanelDoubleClickListener.getPath(e, tree) ?: return false

      val node = selectionPath.lastPathComponent

      if (node is DriverRfsLoadMoreNode) {
        val simpleColoredComponent = (tree.getComponentAt(e.point) as? Tree)?.getDeepestRendererComponentAt(e.x,
                                                                                                            e.y) as? SimpleColoredComponent
        val fragment = simpleColoredComponent?.findFragmentAt(e.x - simpleColoredComponent.x)
        if (node.onClick(simpleColoredComponent, fragment)) return true
      }
    }
    return false
  }
}