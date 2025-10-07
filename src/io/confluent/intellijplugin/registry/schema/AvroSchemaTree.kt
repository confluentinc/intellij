package io.confluent.intellijplugin.registry.schema

import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
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
        val child = if (field != null)
            createMutableNode(fieldName, schema.getSchemaName(), getReadableVal(field.defaultVal()), field.doc())
        else createMutableNode(fieldName, schema.getSchemaName())

        parent.add(child)
        addNestedTypes(child, schema)
    }

    private fun addNestedTypes(parent: DefaultMutableTreeNode, schema: Schema) = when (schema.type) {
        Type.RECORD -> {
            parent.add(createEmptyChild())
            records[parent.getID()] = schema
        }

        Type.UNION -> schema.types?.forEachIndexed { index, schemaItem ->
            addChildren(parent, "type $index", schemaItem)
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

    private fun Schema.getSchemaName(): String = when (this.type) {
        Type.FIXED -> "fixed[${this.fixedSize}]"
        Type.ARRAY -> "array<${this.elementType.typeName()}>"
        Type.MAP -> "map<string, ${this.valueType.typeName()}>"
        Type.UNION -> this.types?.joinToString(" | ") { it.typeName() } ?: this.typeName()
        else -> this.typeName()
    }

    private fun Schema.typeName() = this.type.getName().lowercase()

    override fun buildTree(root: DefaultMutableTreeNode) {
        val rawSchema = schema.rawSchema()
        if (rawSchema.type == Type.RECORD) {
            rawSchema.fields.forEach { addChildren(root, it.name(), it.schema(), it) }
        } else addChildren(root, rawSchema.name, rawSchema)
    }

    override fun treeExpanded(event: TreeExpansionEvent?) {
        if (event == null)
            return

        val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

        val record = records[node.id] ?: return
        expandedNode.removeAllChildren()
        record.fields.forEach { addChildren(expandedNode, it.name(), it.schema(), it) }
        model.nodeStructureChanged(expandedNode)
    }
}