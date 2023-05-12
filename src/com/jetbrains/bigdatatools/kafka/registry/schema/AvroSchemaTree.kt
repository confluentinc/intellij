package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import javax.swing.tree.DefaultMutableTreeNode

class AvroSchemaTree(private val schema: AvroSchema) {
  private fun buildAvroSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, defaultVal: Any? = null) {
    val typeName = when (schema.type) {
      Schema.Type.MAP -> "map<string, ${schema.valueType.type.getName().lowercase()}>"
      Schema.Type.FIXED -> "fixed {${schema.fixedSize}}"
      Schema.Type.ARRAY -> "array<${schema.elementType}>"
      else -> schema.type.getName().lowercase()
    }

    val child = DefaultMutableTreeNode(SchemaRegistryFieldsInfo(fieldName, typeName, defaultVal?.toString() ?: ""))
    parent.add(child)
    when (schema.type) {
      Schema.Type.RECORD -> schema.fields.forEach { buildAvroSchemaTree(child, it.name(), it.schema(), it.defaultVal()) }
      Schema.Type.UNION -> for ((index, value) in schema.types.withIndex()) {
        buildAvroSchemaTree(child, "[$index]", value)
      }
      else -> {}
    }
  }

  fun buildTree(root: DefaultMutableTreeNode) {
    schema.rawSchema().fields.forEach { buildAvroSchemaTree(root, it.name(), it.schema(), it.defaultVal()) }
  }
}