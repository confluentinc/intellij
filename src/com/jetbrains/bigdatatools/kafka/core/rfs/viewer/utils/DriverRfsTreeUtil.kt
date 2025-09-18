package com.jetbrains.bigdatatools.kafka.core.rfs.viewer.utils

import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode
import javax.swing.tree.TreePath

object DriverRfsTreeUtil {
  val TreePath.lastDriverNode: DriverFileRfsTreeNode?
    get() = lastPathComponent as? DriverFileRfsTreeNode
}

