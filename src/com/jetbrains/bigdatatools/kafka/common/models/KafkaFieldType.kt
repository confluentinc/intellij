package com.jetbrains.bigdatatools.kafka.common.models

import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer
import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import com.amazonaws.services.schemaregistry.utils.AvroRecordType
import com.amazonaws.services.schemaregistry.utils.ProtobufMessageType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryFormat
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.Nls
import software.amazon.awssdk.services.glue.model.DataFormat

enum class KafkaFieldType(@Nls val title: String) {
  STRING(KafkaMessagesBundle.message("field.type.string")),
  JSON(KafkaMessagesBundle.message("field.type.json")),
  LONG(KafkaMessagesBundle.message("field.type.long")),
  DOUBLE(KafkaMessagesBundle.message("field.type.double")),
  FLOAT(KafkaMessagesBundle.message("field.type.float")),
  BASE64(KafkaMessagesBundle.message("field.type.base64")),
  NULL(KafkaMessagesBundle.message("field.type.null")),

  SCHEMA_REGISTRY(KafkaMessagesBundle.message("field.type.registry"));

  fun getDeserializationClass(dataManager: KafkaDataManager, consumerField: ConsumerProducerFieldConfig) = when (this) {
    STRING, JSON -> StringDeserializer()
    LONG -> LongDeserializer()
    DOUBLE -> DoubleDeserializer()
    FLOAT -> FloatDeserializer()
    BASE64 -> ByteArrayDeserializer()
    NULL -> VoidDeserializer()
    SCHEMA_REGISTRY -> {
      val registryFormat = consumerField.schemaFormat

      when (dataManager.registryType) {
        KafkaRegistryType.NONE -> error("Non exists")
        KafkaRegistryType.CONFLUENT -> when (registryFormat) {
          KafkaRegistryFormat.AVRO -> KafkaAvroDeserializer(dataManager.client.confluentRegistryClient?.internalClient)
          KafkaRegistryFormat.PROTOBUF -> KafkaProtobufDeserializer(dataManager.client.confluentRegistryClient?.internalClient)
          KafkaRegistryFormat.JSON -> KafkaJsonSchemaDeserializer(dataManager.client.confluentRegistryClient?.internalClient)
          KafkaRegistryFormat.UNKNOWN -> error("Schema is deleted")
        }
        KafkaRegistryType.AWS_GLUE -> when (registryFormat) {
          KafkaRegistryFormat.AVRO -> GlueSchemaRegistryKafkaDeserializer(
            dataManager.client.glueRegistryClient?.credentialsController?.credentials, mapOf(
            AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
            AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
            AWSSchemaRegistryConstants.AWS_REGION to dataManager.client.glueRegistryClient?.region,
            AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.AVRO.name,
            AWSSchemaRegistryConstants.AVRO_RECORD_TYPE to AvroRecordType.GENERIC_RECORD.name,
          ))
          KafkaRegistryFormat.PROTOBUF -> GlueSchemaRegistryKafkaDeserializer(
            dataManager.client.glueRegistryClient?.credentialsController?.credentials,
            mapOf(
              AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
              AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
              AWSSchemaRegistryConstants.AWS_REGION to dataManager.client.glueRegistryClient?.region,
              AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.PROTOBUF.name,
              AWSSchemaRegistryConstants.PROTOBUF_MESSAGE_TYPE to ProtobufMessageType.DYNAMIC_MESSAGE.name
            ))
          KafkaRegistryFormat.JSON -> GlueSchemaRegistryKafkaDeserializer(
            dataManager.client.glueRegistryClient?.credentialsController?.credentials,
            mapOf(
              AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
              AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
              AWSSchemaRegistryConstants.AWS_REGION to dataManager.client.glueRegistryClient?.region,
              AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.JSON.name
            ))
          KafkaRegistryFormat.UNKNOWN -> error("Schema deleted")
        }
      }
    }
  }

  fun getSerializer(dataManager: KafkaDataManager, producerField: ConsumerProducerFieldConfig) = when (this) {
    STRING -> StringSerializer()
    JSON -> StringSerializer()
    LONG -> LongSerializer()
    DOUBLE -> DoubleSerializer()
    FLOAT -> FloatSerializer()
    BASE64 -> ByteArraySerializer()
    NULL -> VoidSerializer()
    SCHEMA_REGISTRY -> {
      val registryFormat = producerField.schemaFormat

      when (dataManager.registryType) {
        KafkaRegistryType.NONE -> error("Non exists")
        KafkaRegistryType.CONFLUENT -> when (registryFormat) {
          KafkaRegistryFormat.AVRO -> BdtKafkaAvroSerializer(dataManager.client.confluentRegistryClient?.internalClient,
                                                             producerField.schemaName)
          KafkaRegistryFormat.PROTOBUF -> BdtKafkaProtobufSerializer(dataManager.client.confluentRegistryClient?.internalClient,
                                                                     producerField.schemaName)
          KafkaRegistryFormat.JSON -> BdtKafkaJsonSchemaSerializer(dataManager.client.confluentRegistryClient?.internalClient,
                                                                   producerField.schemaName)
          KafkaRegistryFormat.UNKNOWN -> error("Schema deleted")
        }
        KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaSerializer(
          dataManager.client.glueRegistryClient?.credentialsController?.credentials,
          dataManager.client.glueRegistryClient?.getLatestVersionId(producerField.schemaName),
          mapOf<String?, String?>(
            AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
            AWSSchemaRegistryConstants.SCHEMA_NAME to producerField.schemaName.ifBlank { null },
            AWSSchemaRegistryConstants.AWS_REGION to dataManager.client.glueRegistryClient?.region,
            AWSSchemaRegistryConstants.DATA_FORMAT to when (registryFormat) {
              KafkaRegistryFormat.AVRO -> DataFormat.AVRO.name
              KafkaRegistryFormat.PROTOBUF -> DataFormat.PROTOBUF.name
              KafkaRegistryFormat.JSON -> DataFormat.JSON.name
              KafkaRegistryFormat.UNKNOWN -> error("Schema is deleted")
            }
          ))
      }
    }
  }


  companion object {
    val defaultValues = listOf(STRING, JSON, LONG, DOUBLE, FLOAT, BASE64, NULL)
    val registryValues = listOf(SCHEMA_REGISTRY)
    val allValues = defaultValues + registryValues
  }
}