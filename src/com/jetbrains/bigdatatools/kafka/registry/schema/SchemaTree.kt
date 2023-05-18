package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import javax.swing.tree.DefaultMutableTreeNode

interface SchemaTree {
  fun buildTree(root: DefaultMutableTreeNode)

  fun createMutableNode(name: String, type: String, default: Any? = null, description: String? = null, optional: Boolean? = null) =
    DefaultMutableTreeNode(SchemaRegistryFieldsInfo(name, type, default?.toString() ?: "", description ?: "", optional))
}