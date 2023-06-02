package com.jetbrains.bigdatatools.kafka.registry.schema

import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JsonSchemaTree(model: DefaultTreeModel, private val schema: JsonSchema) : SchemaTree(model) {
  private fun addChildren(parent: DefaultMutableTreeNode, fieldName: String, schema: Schema, isRequired: Boolean = false) {
    val child = createMutableNode(fieldName, schema.resolveFieldType(), schema.defaultValue, schema.description,
                                  isRequired)
    parent.add(child)
    when (schema) {
      is ObjectSchema -> schema.propertySchemas?.forEach {
        addChildren(child, it.key, it.value, isRequiredField(schema, it.key))
      }
      is CombinedSchema -> schema.subschemas.forEachIndexed { index, value ->
        addChildren(child, "type $index", value)
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
    objectSchema.propertySchemas?.forEach { addChildren(root, it.key, it.value, isRequiredField(objectSchema, it.key)) }
  }

  override fun treeExpanded(event: TreeExpansionEvent?) {
    //TODO
  }

  private fun isRequiredField(schema: ObjectSchema, fieldName: String): Boolean = schema.requiredProperties.contains(fieldName)
}