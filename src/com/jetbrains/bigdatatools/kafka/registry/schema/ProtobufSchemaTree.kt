package com.jetbrains.bigdatatools.kafka.registry.schema

import com.squareup.wire.schema.internal.parser.EnumElement
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.TypeElement
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import javax.swing.tree.DefaultMutableTreeNode

class ProtobufSchemaTree(private val schema: ProtobufSchema) : SchemaTree {
  private val protoTypes = hashMapOf<String, TypeElement>()

  private fun buildProtobufTree(parent: DefaultMutableTreeNode, type: TypeElement) {
    when (type) {
      is MessageElement -> {
        type.fields.forEach {
          val child = createMutableNode(it.name, it.type, it.defaultValue, it.documentation)
          parent.add(child)

          val objectType = protoTypes[it.type]
          if (objectType != null) {
            buildProtobufTree(child, objectType)
          }
        }
        type.nestedTypes.forEach { buildProtobufTree(parent, it) }
      }
      is EnumElement -> {
        for ((index, value) in type.constants.withIndex()) {
          parent.add(createMutableNode("[$index]", value.name))
        }
      }
      else -> {}
    }
  }

  override fun buildTree(root: DefaultMutableTreeNode) {
    val protoFile = schema.rawSchema() ?: return
    val mainMessage = protoFile.types.firstOrNull() as? MessageElement ?: return
    protoFile.types.forEach { protoTypes[it.name] = it }

    buildProtobufTree(root, mainMessage)
  }
}