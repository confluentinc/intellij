package com.jetbrains.bigdatatools.kafka.util.generator

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
  private fun generate(field: FieldDescriptor): Any {
    return when (field.type) {
      DOUBLE -> PrimitivesGenerator.generateDouble()
      FLOAT -> PrimitivesGenerator.generateFloat()
      INT64 -> PrimitivesGenerator.generateLong()
      UINT64 -> PrimitivesGenerator.generateUlong()
      INT32 -> PrimitivesGenerator.generateInt()
      FIXED64 -> PrimitivesGenerator.generateUlong()
      FIXED32 -> PrimitivesGenerator.generateUint()
      BOOL -> PrimitivesGenerator.generateBoolean()
      STRING -> PrimitivesGenerator.generateString()
      BYTES -> PrimitivesGenerator.generateBytes()
      UINT32 -> PrimitivesGenerator.generateInt()
      SFIXED32 -> PrimitivesGenerator.generateInt()
      SFIXED64 -> PrimitivesGenerator.generateLong()
      SINT32 -> PrimitivesGenerator.generateInt()
      SINT64 -> PrimitivesGenerator.generateLong()
      ENUM -> generateEnum(field.enumType)
      GROUP, MESSAGE -> generateMessage(field.messageType)
      else -> throw RuntimeException("Unrecognized schema type: " + field.type)
    }
  }

  private fun generateEnum(field: EnumDescriptor): EnumValueDescriptor {
    val values = field.values
    return values[Random.nextInt(values.size)]
  }

  private fun generateMessage(message: Descriptor): Message {
    // Also possible builder.setUnknownFields()
    val builder = DynamicMessage.newBuilder(message)
    message.fields.forEach {
      if (it.isRepeated) {
        val repeatedItemsSize = Random.nextInt(3, 8)
        for (i in 0..repeatedItemsSize) {
          builder.addRepeatedField(it, generate(it))
        }
      }
      else builder.setField(it, generate(it))
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