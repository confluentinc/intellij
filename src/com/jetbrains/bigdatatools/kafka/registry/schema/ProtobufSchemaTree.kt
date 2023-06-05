package com.jetbrains.bigdatatools.kafka.registry.schema

import com.google.protobuf.Descriptors.Descriptor
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type.*
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.event.TreeExpansionEvent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class ProtobufSchemaTree(model: DefaultTreeModel, private val schema: ProtobufSchema) : SchemaTree(model) {
  private val messages = hashMapOf<String, Descriptor>()

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
        val child = createMutableNode(field.name, field.typeName(), field.defaultValue, required = !field.isOptional)
        parent.add(child)

        enumType.values.forEach { enum ->
          child.add(createMutableNode(enum.name, ""))
        }
      }
      else -> {
        parent.add(createMutableNode(field.name, field.typeName(), getReadableVal(field.defaultValue), required = !field.isOptional))
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
    val descriptor = schema.toDescriptor()
    descriptor.fields.forEach { addChildren(root, it) }
  }

  override fun treeExpanded(event: TreeExpansionEvent?) {
    if (event == null)
      return

    val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
    val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

    val descriptor = messages[node.id] ?: return
    expandedNode.removeAllChildren()
    descriptor.fields.forEach { addChildren(expandedNode, it) }
    model.nodeStructureChanged(expandedNode)
  }
}