package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import javax.swing.event.TreeExpansionEvent
import javax.swing.event.TreeExpansionListener
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

abstract class SchemaTree(protected val model: DefaultTreeModel) : TreeExpansionListener {
  abstract fun buildTree(root: DefaultMutableTreeNode)

  fun createMutableNode(name: String, type: String, default: Any? = null, description: String? = null, required: Boolean? = null) =
    DefaultMutableTreeNode(SchemaRegistryFieldsInfo(name, type, default?.toString() ?: "", description ?: "", required?.toString() ?: ""))

  fun createEmptyChild(): DefaultMutableTreeNode = createMutableNode("", "")

  override fun treeCollapsed(event: TreeExpansionEvent?) {}
}