package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import javax.swing.tree.DefaultMutableTreeNode

class AvroSchemaTree(private val schema: AvroSchema) : SchemaTree {
  private fun buildAvroSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, field: Schema.Field? = null) {
    val nameOfField = if (schema.type == Type.FIXED)
      "$fieldName size=${schema.fixedSize}"
    else fieldName

    val child = if (field != null)
      createMutableNode(nameOfField, schema.typeName(), field.defaultVal(), field.doc())
    else createMutableNode(nameOfField, schema.typeName())

    parent.add(child)
    addNestedTypes(child, schema)
  }

  private fun addNestedTypes(parent: DefaultMutableTreeNode, schema: Schema) = when (schema.type) {
    Type.RECORD -> schema.fields?.forEach { buildAvroSchemaTree(parent, it.name(), it.schema(), it) }
    Type.UNION -> schema.types?.forEachIndexed { index, schemaItem ->
      buildAvroSchemaTree(parent, "[$index]", schemaItem)
    }
    Type.MAP -> {
      parent.add(createMutableNode("key", "string"))
      parent.add(createMutableNode("value", schema.valueType.typeName()))
    }
    Type.ARRAY -> parent.add(createMutableNode("value", schema.elementType.typeName()))
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