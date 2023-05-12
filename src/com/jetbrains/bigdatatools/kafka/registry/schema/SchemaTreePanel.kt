package com.jetbrains.bigdatatools.kafka.registry.schema

import com.intellij.ui.treeStructure.Tree
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class SchemaTreePanel {
  // TODO: It's possible to show additional information
  // JsonSchema -- title, description
  // Avro -- doc
  // Protobuf -- label, documentation, options

  private val tree = Tree().also {
    it.isRootVisible = false
  }

  fun update(schema: ParsedSchema) {
    val root = DefaultMutableTreeNode()

    when (schema) {
      is AvroSchema -> AvroSchemaTree(schema).buildTree(root)
      is JsonSchema -> JsonSchemaTree(schema).buildTree(root)
      is ProtobufSchema -> ProtobufSchemaTree(schema).buildTree(root)
      else -> {}
    }

    tree.model = DefaultTreeModel(root)
    tree.updateUI()
  }

  fun getComponent() = tree
}