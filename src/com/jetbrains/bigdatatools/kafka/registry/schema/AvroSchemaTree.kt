package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import javax.swing.tree.DefaultMutableTreeNode

class AvroSchemaTree(private val schema: AvroSchema) : SchemaTree {
  private fun buildAvroSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, field: Schema.Field? = null) {
    val child = if (field != null)
      createMutableNode(fieldName, schema.typeName(), field.defaultVal(), field.doc())
    else createMutableNode(fieldName, schema.typeName())

    parent.add(child)
    addNestedTypes(child, schema)
  }

  private fun addNestedTypes(parent: DefaultMutableTreeNode, schema: Schema) = when (schema.type) {
    Type.RECORD -> schema.fields.forEach { buildAvroSchemaTree(parent, it.name(), it.schema(), it) }
    Type.UNION -> for ((index, schemaItem) in schema.types.withIndex()) {
      buildAvroSchemaTree(parent, "[$index]", schemaItem)
    }
    Type.MAP -> {
      parent.add(createMutableNode("key", "string"))
      parent.add(createMutableNode("value", schema.valueType.typeName()))
    }
    Type.ARRAY -> parent.add(createMutableNode("value", schema.elementType.typeName()))
    Type.FIXED -> parent.add(createMutableNode("size", schema.fixedSize.toString()))
    Type.ENUM -> schema.enumSymbols?.forEachIndexed { index, enum ->
      parent.add(createMutableNode("[$index]", enum))
    }
    else -> {}
  }

  private fun Schema.typeName() = this.type.getName().lowercase()

  override fun buildTree(root: DefaultMutableTreeNode) {
    val rawSchema = schema.rawSchema()
    addNestedTypes(root, rawSchema)
  }
}