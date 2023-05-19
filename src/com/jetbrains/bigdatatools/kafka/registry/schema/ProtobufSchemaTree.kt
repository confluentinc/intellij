package com.jetbrains.bigdatatools.kafka.registry.schema

import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type.*
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.tree.DefaultMutableTreeNode

class ProtobufSchemaTree(private val schema: ProtobufSchema) : SchemaTree {
  private val messages = mutableListOf<String>()

  private fun buildProtobufTree(parent: DefaultMutableTreeNode, field: FieldDescriptor) {
    val fullName = field.fullName
    if (messages.contains(fullName))
      return
    else messages.add(fullName)

    when (field.type) {
      GROUP, MESSAGE -> {
        val messageType = field.messageType ?: return

        val typeName = if (field.isMapField) "map" else messageType.fullName
        val child = createMutableNode(field.name, typeName, required = !field.isOptional)
        parent.add(child)

        messageType.fields.forEach { buildProtobufTree(child, it) }
      }
      ENUM -> {
        val enumType = field.enumType ?: return
        val child = createMutableNode(field.name, field.typeName(), field.defaultValue, required = !field.isOptional)
        parent.add(child)

        enumType.values.forEachIndexed { index, enum ->
          child.add(createMutableNode("[$index]", enum.name))
        }
      }
      else -> {
        parent.add(createMutableNode(field.name, field.typeName(), field.defaultValue, required = !field.isOptional))
      }
    }
  }

  private fun FieldDescriptor.typeName() = this.type.name.lowercase()

  override fun buildTree(root: DefaultMutableTreeNode) {
    val descriptor = schema.toDescriptor()
    descriptor.fields.forEach { buildProtobufTree(root, it) }
  }
}