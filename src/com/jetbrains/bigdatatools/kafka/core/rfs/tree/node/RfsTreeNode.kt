package com.jetbrains.bigdatatools.kafka.core.rfs.tree.node

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

abstract class RfsTreeNode(project: Project) : AbstractTreeNode<Any>(project, Any()) {
  var error: Throwable? = null

  abstract override fun getChildren(): List<RfsTreeNode>

  abstract val connId: String
  abstract val isMount: Boolean
}