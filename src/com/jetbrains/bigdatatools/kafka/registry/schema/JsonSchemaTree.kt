package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.*
import javax.swing.tree.DefaultMutableTreeNode

class JsonSchemaTree(private val schema: JsonSchema) {
  private fun buildJsonSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema) {
    val type = if (schema is ArraySchema && schema.allItemSchema != null)
      "array<${resolveJsonFieldType(schema.allItemSchema)}>"
    else
      resolveJsonFieldType(schema)

    val child = DefaultMutableTreeNode(SchemaRegistryFieldsInfo(fieldName, type, schema.defaultValue?.toString() ?: "", schema.description,
                                                                schema.isNullable))
    parent.add(child)
    when (schema) {
      is ObjectSchema -> schema.propertySchemas?.forEach { buildJsonSchemaTree(child, it.key, it.value) }
      is ArraySchema -> schema.itemSchemas?.let {
        for ((index, value) in it.withIndex()) {
          buildJsonSchemaTree(child, "[$index]", value)
        }
      }
      else -> {}
    }
  }

  fun buildTree(root: DefaultMutableTreeNode) {
    val objectSchema = schema.rawSchema() as? ObjectSchema ?: return
    objectSchema.propertySchemas?.forEach { buildJsonSchemaTree(root, it.key, it.value) }
  }

  companion object {
    // TODO: CombinedSchema ConditionalSchema ConstSchema EmptySchema EnumSchema FalseSchema NotSchema ReferenceSchema TrueSchema
    fun resolveJsonFieldType(schemaValue: Schema) = when (schemaValue) {
      is NullSchema -> "null"
      is ArraySchema -> "array"
      is BooleanSchema -> "boolean"
      is NumberSchema -> when {
        schemaValue.requiresInteger() -> "integer"
        schemaValue.isRequiresNumber -> "number"
        else -> ""
      }
      is ObjectSchema -> "object"
      is StringSchema -> "string"
      else -> ""
    }
  }
}