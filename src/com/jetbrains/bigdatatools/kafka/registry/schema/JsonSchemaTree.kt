package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.*
import javax.swing.tree.DefaultMutableTreeNode

class JsonSchemaTree(private val schema: JsonSchema) : SchemaTree {
  private lateinit var requiredFields: List<String>

  private fun buildJsonSchemaTree(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema) {
    val child = createMutableNode(fieldName, schema.resolveFieldType(), schema.defaultValue, schema.description,
                                  requiredFields.contains(fieldName))
    parent.add(child)
    when (schema) {
      is ObjectSchema -> schema.propertySchemas?.forEach {
        buildJsonSchemaTree(child, it.key, it.value)
      }
      is CombinedSchema -> schema.subschemas.forEachIndexed { index, value ->
        buildJsonSchemaTree(child, "[$index]", value)
      }
      is ArraySchema -> {
        if (schema.allItemSchema != null) {
          child.add(createMutableNode("value", schema.allItemSchema.resolveFieldType()))
        }
        else schema.itemSchemas?.forEachIndexed { index, value ->
          child.add(createMutableNode("[$index]", value.resolveFieldType()))
        }
      }
      else -> {}
    }
  }

  private fun Schema.resolveFieldType() = when (this) {
    // TODO: CombinedSchema ConditionalSchema EmptySchema NotSchema ReferenceSchema
    is NullSchema -> "null"
    is ArraySchema -> "array"
    is BooleanSchema, is TrueSchema, is FalseSchema -> "boolean"
    is NumberSchema -> when {
      this.requiresInteger() -> "integer"
      this.isRequiresNumber -> "number"
      else -> ""
    }
    is ObjectSchema -> "object"
    is StringSchema -> "string"
    is EnumSchema -> "enum"
    is ConstSchema -> "const"
    else -> ""
  }

  override fun buildTree(root: DefaultMutableTreeNode) {
    val objectSchema = schema.rawSchema() as? ObjectSchema ?: return
    requiredFields = objectSchema.requiredProperties

    objectSchema.propertySchemas?.forEach { buildJsonSchemaTree(root, it.key, it.value) }
  }
}