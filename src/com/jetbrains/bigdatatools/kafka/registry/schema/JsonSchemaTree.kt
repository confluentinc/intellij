package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.*
import javax.swing.tree.DefaultMutableTreeNode

class JsonSchemaTree(private val schema: JsonSchema) : SchemaTree {
  private lateinit var requiredFields: List<String>

  private fun buildJsonSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema) {
    val type = if (schema is ArraySchema && schema.allItemSchema != null)
      "array<${resolveJsonFieldType(schema.allItemSchema)}>"
    else
      resolveJsonFieldType(schema)

    val child = createMutableNode(fieldName, type, schema.defaultValue, schema.description,
                                  requiredFields.contains(fieldName))
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

  override fun buildTree(root: DefaultMutableTreeNode) {
    val objectSchema = schema.rawSchema() as? ObjectSchema ?: return
    requiredFields = objectSchema.requiredProperties
    objectSchema.propertySchemas?.forEach { buildJsonSchemaTree(root, it.key, it.value) }
  }

  companion object {
    // TODO: CombinedSchema ConditionalSchema ConstSchema EmptySchema NotSchema ReferenceSchema
    fun resolveJsonFieldType(schemaValue: Schema) = when (schemaValue) {
      is NullSchema -> "null"
      is ArraySchema -> "array"
      is BooleanSchema, is TrueSchema, is FalseSchema -> "boolean"
      is NumberSchema -> when {
        schemaValue.requiresInteger() -> "integer"
        schemaValue.isRequiresNumber -> "number"
        else -> ""
      }
      is ObjectSchema -> "object"
      is StringSchema -> "string"
      is EnumSchema -> "enum"
      else -> ""
    }
  }
}