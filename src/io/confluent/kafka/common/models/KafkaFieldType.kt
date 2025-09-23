package io.confluent.kafka.common.models

import com.amazonaws.services.schemaregistry.deserializers.GlueSchemaRegistryKafkaDeserializer
import com.amazonaws.services.schemaregistry.serializers.GlueSchemaRegistryKafkaSerializer
import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import com.amazonaws.services.schemaregistry.utils.AvroRecordType
import com.amazonaws.services.schemaregistry.utils.ProtobufMessageType
import io.confluent.kafka.consumer.models.ConsumerProducerFieldConfig
import io.confluent.kafka.data.KafkaDataManager
import io.confluent.kafka.registry.KafkaRegistryFormat
import io.confluent.kafka.registry.KafkaRegistryType
import io.confluent.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer
import org.apache.kafka.common.serialization.*
import org.jetbrains.annotations.Nls
import software.amazon.awssdk.services.glue.model.DataFormat

enum class KafkaFieldType(@Nls val title: String) {
  STRING(KafkaMessagesBundle.message("field.type.string")),
  JSON(KafkaMessagesBundle.message("field.type.json")),
  INTEGER(KafkaMessagesBundle.message("field.type.integer")),
  LONG(KafkaMessagesBundle.message("field.type.long")),
  DOUBLE(KafkaMessagesBundle.message("field.type.double")),
  FLOAT(KafkaMessagesBundle.message("field.type.float")),
  BASE64(KafkaMessagesBundle.message("field.type.base64")),
  NULL(KafkaMessagesBundle.message("field.type.null")),
  PROTOBUF_CUSTOM(KafkaMessagesBundle.message("field.type.custom.protobuf")),
  AVRO_CUSTOM(KafkaMessagesBundle.message("field.type.custom.avro")),
  SCHEMA_REGISTRY(KafkaMessagesBundle.message("field.type.registry"));

  fun getDeserializationClass(dataManager: KafkaDataManager, consumerField: ConsumerProducerFieldConfig) = when (this) {
    STRING, JSON -> StringDeserializer()
    LONG -> LongDeserializer()
    DOUBLE -> DoubleDeserializer()
    INTEGER -> IntegerDeserializer()
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
    PROTOBUF_CUSTOM -> BdtKafkaCustomProtobufDeserializer(consumerField)
    AVRO_CUSTOM -> BdtKafkaCustomAvroDeserializer(consumerField)
  }

  fun getSerializer(dataManager: KafkaDataManager, producerField: ConsumerProducerFieldConfig) = when (this) {
    STRING -> StringSerializer()
    JSON -> StringSerializer()
    LONG -> LongSerializer()
    INTEGER -> IntegerSerializer()
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
                                                             producerField.schemaName, producerField.parsedSchema)
          KafkaRegistryFormat.PROTOBUF -> BdtKafkaProtobufSerializer(dataManager.client.confluentRegistryClient?.internalClient,
                                                                     producerField.schemaName, producerField.parsedSchema)
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
    PROTOBUF_CUSTOM -> BdtKafkaCustomProtobufSerializer()
    AVRO_CUSTOM -> BdtKafkaCustomAvroSerializer(producerField)
  }


  companion object {
    val defaultValues = listOf(STRING, JSON, INTEGER, LONG, DOUBLE, FLOAT, BASE64, NULL, PROTOBUF_CUSTOM, AVRO_CUSTOM)
    val registryValues = listOf(SCHEMA_REGISTRY)
    val allValues = defaultValues + registryValues
  }
}