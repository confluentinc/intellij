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

  private fun buildProtobufTree(parent: DefaultMutableTreeNode, field: FieldDescriptor) {
    when (field.type) {
      GROUP, MESSAGE -> {
        val messageType = field.messageType ?: return

        val typeName = if (field.isMapField) "map" else messageType.name
        val child = createMutableNode(field.name, typeName, required = !field.isOptional)
        parent.add(child)

        child.add(createEmptyChild())
        messages[messageType.name] = messageType
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

  private fun getReadableVal(defaultValue: Any?): String {
    if (defaultValue == null)
      return ""

    val value = defaultValue.toString()
    return when {
      value.contains("Byte") -> "bytes[]"
      value.contains("Null") -> "null"
      else -> value
    }
  }

  private fun FieldDescriptor.typeName() = this.type.name.lowercase()

  override fun buildTree(root: DefaultMutableTreeNode) {
    val descriptor = schema.toDescriptor()
    descriptor.fields.forEach { buildProtobufTree(root, it) }
  }

  override fun treeExpanded(event: TreeExpansionEvent?) {
    if (event == null)
      return

    val expandedNode = event.path.lastPathComponent as? DefaultMutableTreeNode ?: return
    val node = expandedNode.userObject as? SchemaRegistryFieldsInfo ?: return

    val descriptor = messages[node.type] ?: messages["${node.name}Entry"] ?: return
    expandedNode.removeAllChildren()
    descriptor.fields.forEach { buildProtobufTree(expandedNode, it) }
    model.nodeStructureChanged(expandedNode)
  }
}