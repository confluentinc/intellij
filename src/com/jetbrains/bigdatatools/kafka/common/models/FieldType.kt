package com.jetbrains.bigdatatools.kafka.common.models

import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer
import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import com.amazonaws.services.schemaregistry.utils.AvroRecordType
import com.amazonaws.services.schemaregistry.utils.ProtobufMessageType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.Nls
import software.amazon.awssdk.services.glue.model.DataFormat

enum class FieldType(@Nls val title: String) {
  STRING(KafkaMessagesBundle.message("field.type.string")),
  JSON(KafkaMessagesBundle.message("field.type.json")),
  LONG(KafkaMessagesBundle.message("field.type.long")),
  DOUBLE(KafkaMessagesBundle.message("field.type.double")),
  FLOAT(KafkaMessagesBundle.message("field.type.float")),
  BASE64(KafkaMessagesBundle.message("field.type.base64")),
  NULL(KafkaMessagesBundle.message("field.type.null")),

  AVRO_REGISTRY(KafkaMessagesBundle.message("field.type.avro.registry")),
  PROTOBUF_REGISTRY(KafkaMessagesBundle.message("field.type.protobuf.registry")),
  JSON_REGISTRY(KafkaMessagesBundle.message("field.type.json.registry"));

  fun getDeserializationClass(dataManager: KafkaDataManager, consumerField: ConsumerProducerFieldConfig) = when (this) {
    STRING, JSON -> StringDeserializer()
    LONG -> LongDeserializer()
    DOUBLE -> DoubleDeserializer()
    FLOAT -> FloatDeserializer()
    BASE64 -> ByteArrayDeserializer()
    NULL -> VoidDeserializer()
    AVRO_REGISTRY -> when (dataManager.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> KafkaAvroDeserializer(dataManager.confluentSchemaRegistry?.client?.internalClient)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaDeserializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials, mapOf(
        AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
        AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
        AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
        AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.AVRO.name,
        AWSSchemaRegistryConstants.AVRO_RECORD_TYPE to AvroRecordType.GENERIC_RECORD.name,

        ))
    }
    PROTOBUF_REGISTRY -> when (dataManager.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> KafkaProtobufDeserializer(dataManager.confluentSchemaRegistry?.client?.internalClient)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaDeserializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials,
        mapOf(
          AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
          AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
          AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
          AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.PROTOBUF.name,
          AWSSchemaRegistryConstants.PROTOBUF_MESSAGE_TYPE to ProtobufMessageType.DYNAMIC_MESSAGE.name
        ))
    }
    JSON_REGISTRY -> when (dataManager.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> KafkaJsonSchemaDeserializer(dataManager.confluentSchemaRegistry?.client?.internalClient)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaDeserializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials,
        mapOf(
          AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
          AWSSchemaRegistryConstants.SCHEMA_NAME to consumerField.schemaName.ifBlank { null },
          AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
          AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.JSON.name
        ))
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
    AVRO_REGISTRY -> when (producerField.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> BdtKafkaAvroSerializer(dataManager.confluentSchemaRegistry?.client?.internalClient,
                                                            producerField.schemaName)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaSerializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials,
        dataManager.glueSchemaRegistry?.getLastVersionSchemaInfo(producerField.schemaName),
        mapOf(
          AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
          AWSSchemaRegistryConstants.SCHEMA_NAME to producerField.schemaName.ifBlank { null },
          AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
          AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.AVRO.name
        ))
    }
    PROTOBUF_REGISTRY -> when (dataManager.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> BdtKafkaProtobufSerializer(dataManager.confluentSchemaRegistry?.client?.internalClient,
                                                                producerField.schemaName)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaSerializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials,
        dataManager.glueSchemaRegistry?.getLastVersionSchemaInfo(producerField.schemaName),
        mapOf(
          AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
          AWSSchemaRegistryConstants.SCHEMA_NAME to producerField.schemaName.ifBlank { null },
          AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
          AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.PROTOBUF.name
        ))
    }
    JSON_REGISTRY -> when (dataManager.registryType) {
      KafkaRegistryType.NONE -> error("Non exists")
      KafkaRegistryType.CONFLUENT -> BdtKafkaJsonSchemaSerializer(dataManager.confluentSchemaRegistry?.client?.internalClient,
                                                                  producerField.schemaName)
      KafkaRegistryType.AWS_GLUE -> GlueSchemaRegistryKafkaSerializer(
        dataManager.glueSchemaRegistry?.client?.credentialsController?.credentials,
        dataManager.glueSchemaRegistry?.getLastVersionSchemaInfo(producerField.schemaName),
        mapOf(
          AWSSchemaRegistryConstants.REGISTRY_NAME to dataManager.connectionData.getGlueRegistryOrDefault(),
          AWSSchemaRegistryConstants.SCHEMA_NAME to producerField.schemaName.ifBlank { null },
          AWSSchemaRegistryConstants.AWS_REGION to dataManager.glueSchemaRegistry?.region,
          AWSSchemaRegistryConstants.DATA_FORMAT to DataFormat.JSON.name
        ))
    }
  }


  companion object {
    val defaultValues = listOf(STRING, JSON, LONG, DOUBLE, FLOAT, BASE64, NULL)
    val registryValues = listOf(AVRO_REGISTRY, PROTOBUF_REGISTRY, JSON_REGISTRY)
    val allValues = defaultValues + registryValues

    const val KEY_PARSED_SCHEMA_CONFIG_KEY = "bdt.registry.key.schema.custom"
    const val VALUE_PARSED_SCHEMA_CONFIG_KEY = "bdt.registry.value.schema.custom"
  }
}