package com.jetbrains.bigdatatools.kafka.registry

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.openapi.util.NlsSafe
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.bigdatatools.kafka.common.models.KafkaFieldType
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.confluent.ConfluentRegistryClient
import com.jetbrains.bigdatatools.kafka.registry.serde.BdtJsonSchemaProvider
import com.jetbrains.bigdatatools.kafka.registry.serde.BdtProtobufSchemaProvider
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchema
import java.util.logging.Level
import java.util.logging.Logger

object KafkaRegistryUtil {
  fun getRegistrySchemaProviders() = listOf(AvroSchemaProvider(), BdtProtobufSchemaProvider(), BdtJsonSchemaProvider())

  val protobufLanguage: Language
    get() = Language.findLanguageByID("protobuf") ?: PlainTextLanguage.INSTANCE

  // We need to disable loggers in schemaregistry, because there was a lot ot error messages (for example AvroSchemaProvider) in case of exceptions,
  // while we are processing that exceptions on our own. And every error message in log produces IDE error notification.
  fun disableLoggers() {
    Logger.getLogger("io.confluent.kafka.schemaregistry").level = Level.OFF
  }

  fun getSchemaType(schemaName: String,
                    dataManager: KafkaDataManager) = dataManager.getCachedOrLoadSchema(schemaName).type

  @RequiresBackgroundThread
  fun loadSchema(schemaName: String,
                 fieldType: KafkaFieldType,
                 dataManager: KafkaDataManager): ParsedSchema? {
    if (fieldType !in KafkaFieldType.registryValues)
      return null

    val versionInfo = dataManager.getLatestVersionInfo(schemaName) ?: return null
    return parseSchema(versionInfo.type, versionInfo.schema, dataManager, versionInfo.references).getOrThrow()
  }

  fun parseSchema(schemaType: KafkaRegistryFormat,
                  newText: @NlsSafe String,
                  dataManager: KafkaDataManager,
                  references: List<SchemaReference> = emptyList()): Result<ParsedSchema> =
    parseSchema(schemaType, newText, dataManager.client.confluentRegistryClient, references)

  fun parseSchema(schemaType: KafkaRegistryFormat,
                  newText: @NlsSafe String,
                  client: ConfluentRegistryClient?,
                  references: List<SchemaReference> = emptyList()): Result<ParsedSchema> {
    return try {
      val providers = client?.internalClient?.schemaProviders?.values ?: getRegistrySchemaProviders()
      val provider = providers.firstOrNull {
        it.schemaType() == schemaType.name
      } ?: error("Schema type is not found ${schemaType}")

      val value = provider.parseSchemaOrElseThrow(newText, references, true)
      Result.success(value)
    }
    catch (e: Throwable) {
      Result.failure(e)
    }
  }

  fun getPrettySchema(schemaType: String, schema: String): String = if (schemaType == ProtobufSchema.TYPE) {
    schema
  }
  else {
    val gson = GsonBuilder().setPrettyPrinting().create()
    val jsonObject = gson.fromJson(schema.ifBlank { null } ?: "{}", JsonElement::class.java)
    gson.toJson(jsonObject)
  }

  fun parseRecordName(schema: ParsedSchema?): String? = schema?.name()
}