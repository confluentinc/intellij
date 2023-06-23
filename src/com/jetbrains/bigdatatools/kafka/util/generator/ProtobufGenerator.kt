package com.jetbrains.bigdatatools.kafka.util.generator

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.*
import com.google.protobuf.Descriptors.FieldDescriptor.Type.*
import com.google.protobuf.DynamicMessage
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.util.JsonFormat.TypeRegistry
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import kotlin.random.Random

class ProtobufGenerator(private val schema: ProtobufSchema) {
  private val messages = mutableSetOf<String>()

  private fun generate(field: FieldDescriptor): Any? {
    return when (field.type) {
      DOUBLE -> PrimitivesGenerator.generateDouble()
      FLOAT -> PrimitivesGenerator.generateFloat()
      INT64 -> PrimitivesGenerator.generateLong()
      UINT64 -> PrimitivesGenerator.generateUlong().toLong()
      INT32 -> PrimitivesGenerator.generateInt()
      FIXED64 -> PrimitivesGenerator.generateUlong().toLong()
      FIXED32 -> PrimitivesGenerator.generateUint().toInt()
      BOOL -> PrimitivesGenerator.generateBoolean()
      STRING -> PrimitivesGenerator.generateString()
      BYTES -> ByteString.copyFrom(PrimitivesGenerator.generateBytes())
      UINT32 -> PrimitivesGenerator.generateUint().toInt()
      SFIXED32 -> PrimitivesGenerator.generateInt()
      SFIXED64 -> PrimitivesGenerator.generateLong()
      SINT32 -> PrimitivesGenerator.generateInt()
      SINT64 -> PrimitivesGenerator.generateLong()
      ENUM -> generateEnum(field.enumType)
      GROUP, MESSAGE -> {
        val messageType = field.messageType
        if (messages.contains(messageType.fullName)) null else generateMessage(messageType)
      }
      else -> throw RuntimeException("Unrecognized schema type: " + field.type)
    }
  }

  private fun generateEnum(field: EnumDescriptor): EnumValueDescriptor {
    val values = field.values
    return values[Random.nextInt(values.size)]
  }

  private fun generateMessage(message: Descriptor): Message {
    messages.add(message.fullName)

    // Also possible builder.setUnknownFields()
    val builder = DynamicMessage.newBuilder(message)
    message.fields.forEach { field ->
      if (field.isRepeated) {
        val repeatedItemsSize = Random.nextInt(3, 8)
        for (i in 0..repeatedItemsSize) {
          generate(field)?.let { data -> builder.addRepeatedField(field, data) }
        }
      }
      else generate(field)?.let { data -> builder.setField(field, data) }
    }

    return builder.build()
  }

  private fun generate(): String {
    val descriptor = schema.toDescriptor()
    val generatedMessage = generateMessage(descriptor)

    val registryBuilder = TypeRegistry.newBuilder()
    val jsonFormat: JsonFormat.Printer = JsonFormat.printer().usingTypeRegistry(registryBuilder.build())

    return jsonFormat.print(generatedMessage)
  }

  companion object {
    fun generateProtobufMessage(schema: ParsedSchema?): String {
      val protobufSchema = schema as? ProtobufSchema
      if (protobufSchema == null) {
        GenerateRandomData.logger.warn("Schema could not be null and the type of it should be AVRO")
        return ""
      }

      return ProtobufGenerator(protobufSchema).generate()
    }
  }
}