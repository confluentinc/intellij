package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import javax.swing.tree.DefaultMutableTreeNode

class AvroSchemaTree(private val schema: AvroSchema) : SchemaTree {
  private fun buildAvroSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, field: Schema.Field? = null) {
    val typeName = when (schema.type) {
      Schema.Type.MAP -> "map<string, ${schema.valueType.type.getName().lowercase()}>"
      Schema.Type.FIXED -> "fixed size=${schema.fixedSize}"
      Schema.Type.ARRAY -> "array<${schema.elementType}>"
      else -> schema.type.getName().lowercase()
    }

    val child = if (field != null)
      createMutableNode(fieldName, typeName, field.defaultVal(), field.doc())
    else createMutableNode(fieldName, typeName)

    parent.add(child)
    addNestedTypes(child, schema)
  }

  private fun addNestedTypes(parent: DefaultMutableTreeNode, schema: Schema) = when (schema.type) {
    Schema.Type.RECORD -> schema.fields.forEach { buildAvroSchemaTree(parent, it.name(), it.schema(), it) }
    Schema.Type.UNION -> for ((index, schemaItem) in schema.types.withIndex()) {
      buildAvroSchemaTree(parent, "[$index]", schemaItem)
    }
    else -> {}
  }

  override fun buildTree(root: DefaultMutableTreeNode) {
    val rawSchema = schema.rawSchema()
    addNestedTypes(root, rawSchema)
  }
}