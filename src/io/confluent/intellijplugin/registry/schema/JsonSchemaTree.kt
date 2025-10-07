package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.everit.json.schema.*
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class JsonSchemaTree(model: DefaultTreeModel, private val schema: JsonSchema) : SchemaTree(model) {
    private val objects = hashMapOf<String, ObjectSchema>()

    private fun addChildren(
        parent: DefaultMutableTreeNode,
        fieldName: String,
        schema: Schema,
        isRequired: Boolean = false
    ) {
        getConstOrEnumSchema(schema)?.let {
            addChildren(parent, fieldName, it, isRequired)
            return
        }

        if (schema is ReferenceSchema) {
            addChildren(parent, fieldName, schema.referredSchema)
            return
        }

        val child = createMutableNode(
            fieldName, schema.resolveFieldType(), schema.defaultValue, schema.description,
            isRequired
        )
        parent.add(child)
        when (schema) {
            is ObjectSchema -> {
                child.add(createEmptyChild())
                objects[child.getID()] = schema
            }

            is CombinedSchema -> schema.subschemas.forEachIndexed { index, value ->
                addChildren(child, "type $index", value)
            }

            is ArraySchema -> {
                if (schema.allItemSchema != null) {
                    child.add(createMutableNode("value", schema.allItemSchema.resolveFieldType()))
                } else schema.itemSchemas?.forEachIndexed { index, value ->
                    child.add(createMutableNode("[$index]", value.resolveFieldType()))
                }
            }

            is EnumSchema -> schema.possibleValues.forEach { child.add(createMutableNode(it.toString(), "")) }
            is ConstSchema -> child.add(createMutableNode(schema.permittedValue.toString(), ""))
            else -> {}
        }
    }

    private fun getConstOrEnumSchema(schema: Schema): Schema? {
        val subSchemas = (schema as? CombinedSchema)?.subschemas ?: return null
        return subSchemas.firstOrNull { it is ConstSchema } ?: subSchemas.firstOrNull { it is EnumSchema }
    }

    private fun Schema.resolveFieldType(): String = when (this) {
        // TODO: ConditionalSchema NotSchema
        is NullSchema -> "null"
        is ArraySchema -> if (this.allItemSchema != null) "array<${this.allItemSchema.resolveFieldType()}>" else "array<>"
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
        is CombinedSchema -> this.subschemas.joinToString(" | ") { it.resolveFieldType() }
        is ReferenceSchema -> this.referredSchema.resolveFieldType()
        else -> ""
    }

    private fun isRequiredField(schema: ObjectSchema, fieldName: String): Boolean =
        schema.requiredProperties.contains(fieldName)

    override fun buildTree(root: DefaultMutableTreeNode) {
        val objectSchema = schema.rawSchema()
        if (objectSchema is ObjectSchema) {
            objectSchema.propertySchemas?.forEach {
                addChildren(
                    root,
                    it.key,
                    it.value,
                    isRequiredField(objectSchema, it.key)
                )
            }
        } else {
            addChildren(root, "field", objectSchema)
        }
    }

    override fun treeExpanded(event: TreeExpansionEvent?) {
        if (event == null)
            return

        val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

        val objectSchema = objects[node.id] ?: return
        expandedNode.removeAllChildren()
        objectSchema.propertySchemas?.forEach {
            addChildren(
                expandedNode,
                it.key,
                it.value,
                isRequiredField(objectSchema, it.key)
            )
        }
        model.nodeStructureChanged(expandedNode)
    }
}