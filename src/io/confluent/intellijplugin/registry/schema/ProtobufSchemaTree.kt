package io.confluent.intellijplugin.registry.schema

import com.google.protobuf.Descriptors.*
import com.google.protobuf.Descriptors.FieldDescriptor.Type.*
import io.confluent.intellijplugin.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ProtobufSchemaTree(model: DefaultTreeModel, private val schema: ProtobufSchema) : SchemaTree(model) {
    private val messages = hashMapOf<String, Descriptor>()
    private val oneOfFields = mutableSetOf<String>()

    private fun addChildren(parent: DefaultMutableTreeNode, field: FieldDescriptor) {
        when (field.type) {
            GROUP, MESSAGE -> {
                val messageType = field.messageType ?: return

                val typeName = if (field.isMapField) "map<string, ${messageType.mapKeyName()}>" else messageType.name
                val child = createMutableNode(field.name, typeName, required = !field.isOptional)
                parent.add(child)

                child.add(createEmptyChild())
                messages[child.getID()] = messageType
            }

            ENUM -> {
                val enumType = field.enumType ?: return
                val child =
                    createMutableNode(field.name, field.typeName(), field.defaultValue, required = !field.isOptional)
                parent.add(child)

                enumType.values.forEach { enum ->
                    child.add(createMutableNode(enum.name, ""))
                }
            }

            else -> {
                parent.add(
                    createMutableNode(
                        field.name,
                        field.typeName(),
                        getReadableVal(field.defaultValue),
                        required = !field.isOptional
                    )
                )
            }
        }
    }

    private fun Descriptor.mapKeyName(): String {
        val keyType = this.fields[1]
        return when (keyType.type) {
            GROUP, MESSAGE -> keyType.messageType.name
            else -> keyType.typeName()
        }
    }

    private fun FieldDescriptor.typeName() = this.type.name.lowercase()

    override fun buildTree(root: DefaultMutableTreeNode) {
        initFields(root, schema.toDescriptor().fields)
    }

    override fun treeExpanded(event: TreeExpansionEvent?) {
        if (event == null)
            return

        val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

        val descriptor = messages[node.id] ?: return
        expandedNode.removeAllChildren()
        initFields(expandedNode, descriptor.fields)
        model.nodeStructureChanged(expandedNode)
    }

    private fun initFields(parent: DefaultMutableTreeNode, fields: List<FieldDescriptor>) {
        fields.forEach { field ->
            field.realContainingOneof?.let {
                handleOneOfFields(parent, it)
            } ?: addChildren(parent, field)
        }
    }

    private fun handleOneOfFields(parent: DefaultMutableTreeNode, oneOf: OneofDescriptor) {
        if (oneOfFields.contains(oneOf.fullName)) {
            return
        } else {
            oneOfFields.add(oneOf.fullName)

            val node = createMutableNode(oneOf.name, "oneOf")
            parent.add(node)
            oneOf.fields.forEach { addChildren(node, it) }
        }
    }
}