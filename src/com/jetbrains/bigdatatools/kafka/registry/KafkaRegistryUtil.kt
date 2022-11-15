package com.jetbrains.bigdatatools.kafka.registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryInfo
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.ProtoFileElement
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.SchemaMetadata
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.apache.avro.Schema
import org.everit.json.schema.*

object KafkaRegistryUtil {
  val registrySchemaProviders = listOf(AvroSchemaProvider(), ProtobufSchemaProvider(), JsonSchemaProvider())

  fun validateSchema(registryInfo: SchemaRegistryInfo,
                     newText: @NlsSafe String): ParsedSchema {
    val schemaType = registryInfo.meta?.schemaType ?: error("Metainfo is not exists for ${registryInfo.name}")
    val references = registryInfo.meta.references
    return parseSchema(schemaType, newText, references)
  }


  val FieldType.schemaType: String?
    get() = when (this) {
      FieldType.JSON,
      FieldType.STRING,
      FieldType.LONG,
      FieldType.DOUBLE,
      FieldType.FLOAT,
      FieldType.BASE64,
      FieldType.NULL -> null
      FieldType.AVRO_REGISTRY -> AvroSchema.TYPE
      FieldType.PROTOBUF_REGISTRY -> ProtobufSchema.TYPE
      FieldType.JSON_REGISTRY -> JsonSchema.TYPE
    }


  fun parseSchema(meta: SchemaMetadata) = parseSchema(meta.schemaType, meta.schema, meta.references)

  fun parseSchema(schemaType: String?,
                  newText: @NlsSafe String,
                  references: List<SchemaReference> = emptyList()): ParsedSchema {
    val provider = registrySchemaProviders.firstOrNull {
      it.schemaType() == schemaType
    } ?: error("Schema type is not found ${schemaType}")
    return provider.parseSchemaOrElseThrow(newText, references, true)
  }

  fun getPrettySchema(registryInfo: SchemaRegistryInfo): String? {
    val schema = if (registryInfo.meta?.schemaType == ProtobufSchema.TYPE)
      registryInfo.meta.schema
    else {
      val gson = GsonBuilder().setPrettyPrinting().create()
      val jsonObject = gson.fromJson(registryInfo.meta?.schema ?: "{}", JsonObject::class.java)
      gson.toJson(jsonObject)
    }
    return schema
  }

  fun parseRecordName(schema: ParsedSchema?): String? = schema?.name()


  fun parseFields(schema: ParsedSchema?): List<SchemaRegistryFieldsInfo> =
    when (schema?.schemaType()) {
      AvroSchema.TYPE -> parseAvroFields(schema.rawSchema())
      ProtobufSchema.TYPE -> parseProtobufSchemaFields(schema.rawSchema())
      JsonSchema.TYPE -> parseJsonSchemaFields(schema.rawSchema())
      else -> emptyList()
    } ?: emptyList()

  private fun parseAvroFields(rawSchema: Any?): List<SchemaRegistryFieldsInfo>? {
    val avroSchema = rawSchema as? Schema ?: return emptyList()
    val fields = avroSchema.fields?.map {
      SchemaRegistryFieldsInfo(it.name(), it.schema().type.getName().lowercase(), it.defaultVal()?.toString() ?: "")
    }
    return fields
  }

  private fun parseProtobufSchemaFields(rawSchema: Any?): List<SchemaRegistryFieldsInfo> {
    val protoSchema = (rawSchema as? ProtoFileElement)?.types?.firstOrNull() as? MessageElement ?: return emptyList()
    return protoSchema.fields.map {
      SchemaRegistryFieldsInfo(it.name, it.type, it.defaultValue ?: "")
    }
  }

  private fun parseJsonSchemaFields(rawSchema: Any?): List<SchemaRegistryFieldsInfo>? {
    val schema = rawSchema as? ObjectSchema ?: return emptyList()
    val fields = schema.propertySchemas?.map {
      val schemaValue = it.value
      val type = resolveJsonFieldType(schemaValue)
      SchemaRegistryFieldsInfo(it.key, type, schemaValue.defaultValue?.toString() ?: "")
    }
    return fields
  }

  private fun resolveJsonFieldType(schemaValue: org.everit.json.schema.Schema) = when (schemaValue) {
    is NullSchema -> "null"
    is ArraySchema -> "array"
    is BooleanSchema -> "boolean"
    is NumberSchema -> when {
      schemaValue.requiresInteger() -> "integer"
      schemaValue.isRequiresNumber -> "number"
      else -> ""
    }
    is ObjectSchema -> "object"
    is StringSchema -> "string"
    else -> ""
  }
}