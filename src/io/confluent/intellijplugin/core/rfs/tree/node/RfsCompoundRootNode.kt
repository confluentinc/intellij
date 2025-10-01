package io.confluent.intellijplugin.core.rfs.tree.node

import com.intellij.ide.projectView.PresentationData
import com.intellij.openapi.project.Project

class RfsCompoundRootNode(project: Project, val askChildren: () -> List<RfsTreeNode>) : RfsTreeNode(project) {
  override val isMount: Boolean = true
  override val connId: String = ""
  override fun getChildren() = askChildren()
  override fun update(presentation: PresentationData) {}
  override fun isAlwaysExpand(): Boolean = true
  override fun toString(): String = "<Compound root>"
}