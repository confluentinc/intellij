package com.jetbrains.bigdatatools.kafka.registry.schema

import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.squareup.wire.schema.internal.parser.EnumElement
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.TypeElement
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.tree.DefaultMutableTreeNode

class ProtobufSchemaTree(private val schema: ProtobufSchema) {
  private val protoTypes = hashMapOf<String, TypeElement>()

  private fun buildTree(parent: DefaultMutableTreeNode, type: TypeElement) {
    when (type) {
      is MessageElement -> {
        type.fields.forEach {
          val child = DefaultMutableTreeNode(SchemaRegistryFieldsInfo(it.name, it.type, it.defaultValue ?: "", it.documentation))
          parent.add(child)

          val objectType = protoTypes[it.type]
          if (objectType != null) {
            buildTree(child, objectType)
          }
        }
        type.nestedTypes.forEach { buildTree(parent, it) }
      }
      is EnumElement -> {
        for ((index, value) in type.constants.withIndex()) {
          parent.add(DefaultMutableTreeNode(SchemaRegistryFieldsInfo("[$index]", value.name, "")))
        }
      }
      else -> {}
    }
  }

  fun buildTree(root: DefaultMutableTreeNode) {
    val protoFile = schema.rawSchema() ?: return
    val mainMessage = protoFile.types.firstOrNull() as? MessageElement ?: return
    protoFile.types.forEach { protoTypes[it.name] = it }

    buildTree(root, mainMessage)
  }
}