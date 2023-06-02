package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.Schema.Field
import org.apache.avro.Schema.Type
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class AvroSchemaTree(model: DefaultTreeModel, private val schema: AvroSchema) : SchemaTree(model) {
  private val records = hashMapOf<String, Schema>()

  private fun addChildren(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, field: Field? = null) {
    val nameOfField = if (schema.type == Type.FIXED)
      "$fieldName [${schema.fixedSize}]"
    else fieldName

    val child = if (field != null)
      createMutableNode(nameOfField, schema.typeName(), getReadableVal(field.defaultVal()), field.doc())
    else createMutableNode(nameOfField, schema.typeName())

    parent.add(child)
    addNestedTypes(child, fieldName, schema)
  }

  private fun addNestedTypes(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema) = when (schema.type) {
    Type.RECORD -> {
      parent.add(createEmptyChild())
      records[fieldName] = schema
    }
    Type.UNION -> schema.types?.forEachIndexed { index, schemaItem ->
      addChildren(parent, "[$index]", schemaItem)
    }
    Type.MAP -> {
      parent.add(createMutableNode("key", "string"))
      addChildren(parent, "value", schema.valueType)
    }
    Type.ARRAY -> addChildren(parent, "value", schema.elementType)
    Type.ENUM -> schema.enumSymbols?.forEach { enum ->
      parent.add(createMutableNode(enum, ""))
    }
    else -> {}
  }

  private fun Schema.typeName() = this.type.getName().lowercase()

  override fun buildTree(root: DefaultMutableTreeNode) {
    val rawSchema = schema.rawSchema()
    if (rawSchema.type == Type.RECORD) {
      rawSchema.fields.forEach { addChildren(root, it.name(), it.schema(), it) }
    }
    else addChildren(root, rawSchema.name, rawSchema)
  }

  override fun treeExpanded(event: TreeExpansionEvent?) {
    if (event == null)
      return

    val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
    val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

    val record = records[node.name] ?: return
    expandedNode.removeAllChildren()
    record.fields.forEach { addChildren(expandedNode, it.name(), it.schema(), it) }
    model.nodeStructureChanged(expandedNode)
  }
}