package com.jetbrains.bigdatatools.kafka.registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.SchemaRegistryFieldsInfo
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentSchemaInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.squareup.wire.schema.internal.parser.MessageElement
import com.squareup.wire.schema.internal.parser.ProtoFileElement
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.json.JsonSchema
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider
import org.apache.avro.Schema
import org.everit.json.schema.*
import software.amazon.awssdk.services.glue.model.DataFormat
import java.util.logging.Level
import java.util.logging.Logger

object KafkaRegistryUtil {
  val registrySchemaProviders = listOf(AvroSchemaProvider(), ProtobufSchemaProvider(), JsonSchemaProvider())

  // We need to disable loggers in schemaregistry, because there was a lot ot error messages (for example AvroSchemaProvider) in case of exceptions,
  // while we are processing that exceptions on our own. And every error message in log produces IDE error notification.
  fun disableLoggers() {
    Logger.getLogger("io.confluent.kafka.schemaregistry").level = Level.OFF
  }

  fun parseSchema(registryInfo: ConfluentSchemaInfo,
                  newText: @NlsSafe String): ParsedSchema? {
    val schemaType = registryInfo.meta?.schemaType ?: error("Metainfo is not exists for ${registryInfo.name}")
    val references = registryInfo.meta.references
    return parseSchema(schemaType, newText, references).getOrNull()
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

  fun loadSchema(config: ConsumerProducerFieldConfig, dataManager: KafkaDataManager): ParsedSchema? {
    if (config.type !in FieldType.registryValues)
      return null

    return when (config.registryType) {
      KafkaRegistryType.NONE -> null
      KafkaRegistryType.CONFLUENT -> parseConfluentSchema(config, dataManager)
      KafkaRegistryType.AWS_GLUE -> parseGlueSchema(config, dataManager)
    }
  }


  private fun parseConfluentSchema(config: ConsumerProducerFieldConfig, dataManager: KafkaDataManager): ParsedSchema {
    val schemaMetadata = dataManager.confluentSchemaRegistry?.getRegistrySchema(config.schemaName)?.meta
                         ?: error("Schema `${config.schemaName}` is not found")
    return parseSchema(schemaMetadata.schemaType, schemaMetadata.schema, schemaMetadata.references).getOrThrow()
  }

  private fun parseGlueSchema(config: ConsumerProducerFieldConfig, dataManager: KafkaDataManager): ParsedSchema {
    val schemaName = config.schemaName
    val registryName = config.registryName

    val detailedInfo = dataManager.glueSchemaRegistry?.loadDetailedSchemaInfo(schemaName) ?: throw Exception(
      KafkaMessagesBundle.message("error.glue.schema.is.not.found", schemaName, registryName))

    val dataFormat = when (config.type) {
      FieldType.AVRO_REGISTRY -> DataFormat.AVRO
      FieldType.PROTOBUF_REGISTRY -> DataFormat.PROTOBUF
      FieldType.JSON_REGISTRY -> DataFormat.JSON
      else -> null
    }

    val expectedFormat = detailedInfo.schemaResponse.dataFormat()
    if (dataFormat != expectedFormat) {
      throw Exception(KafkaMessagesBundle.message("error.glue.wrong.format", dataFormat?.name ?: "<unknown>", expectedFormat))
    }
    return parseSchema(schemaType = detailedInfo.schemaResponse.dataFormatAsString(),
                       detailedInfo.versionResponse.schemaDefinition(), emptyList()).getOrThrow()
  }

  fun parseSchema(schemaType: String?,
                  newText: @NlsSafe String,
                  references: List<SchemaReference> = emptyList()): Result<ParsedSchema> {
    val provider = registrySchemaProviders.firstOrNull {
      it.schemaType() == schemaType
    } ?: error("Schema type is not found ${schemaType}")

    return try {
      Result.success(provider.parseSchemaOrElseThrow(newText, references, true))
    }
    catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun getPrettySchema(schemaType: String, schema: String): String? {
    return if (schemaType == ProtobufSchema.TYPE) {
      schema
    }
    else {
      val gson = GsonBuilder().setPrettyPrinting().create()
      val jsonObject = gson.fromJson(schema.ifBlank { null } ?: "{}", JsonElement::class.java)
      gson.toJson(jsonObject)
    }
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

    val fields = if (avroSchema.type == Schema.Type.RECORD)
      avroSchema.fields?.map {
        SchemaRegistryFieldsInfo(it.name(), it.schema().type.getName().lowercase(), it.defaultVal()?.toString() ?: "")
      }
    else {
      emptyList()
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