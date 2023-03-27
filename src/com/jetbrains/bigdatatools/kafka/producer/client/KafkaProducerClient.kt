package com.jetbrains.bigdatatools.kafka.producer.client

import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.common.models.ProducerField
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.context.NullContextNameStrategy
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*
import java.util.concurrent.TimeUnit

class KafkaProducerClient(val client: KafkaClient) {
  val connectionData = client.connectionData

  fun sentMessage(dataManager: KafkaDataManager,
                  topic: String, key: ProducerField, value: ProducerField,
                  headers: List<Property> = emptyList(),
                  recordCompression: RecordCompression = RecordCompression.NONE,
                  acks: AcksType = AcksType.NONE,
                  enableIdempotence: Boolean = false,
                  forcePartition: Int = -1,
                  registryName: String?): ProducerResultMessage {
    val props = client.kafkaProps.clone() as Properties
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = key.type.getSerializer(
      dataManager.confluentSchemaRegistry?.client?.internalClient)::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = value.type.getSerializer(
      dataManager.confluentSchemaRegistry?.client?.internalClient)::class.java
    props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.lowercase()
    props[AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY] = NullContextNameStrategy::class.java
    props[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
    props[AWSSchemaRegistryConstants.REGISTRY_NAME] = registryName ?: ""

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        connectionData.registryUrl?.let { props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = it }
        key.registryStrategy?.let {
          props[AbstractKafkaSchemaSerDeConfig.KEY_SUBJECT_NAME_STRATEGY] = it::class.java
        }

        value.registryStrategy?.let {
          props[AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY] = it::class.java
        }
      }
      KafkaRegistryType.AWS_GLUE -> {
        props.put(AWSSchemaRegistryConstants.SCHEMA_NAME, "my-schema")
        props.put(AWSSchemaRegistryConstants.REGISTRY_NAME, registryName)
      }
    }

    if (enableIdempotence)
      props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
    else
      props[ProducerConfig.ACKS_CONFIG] = acks.value.toString()

    val producer = withPluginClassLoader {
      KafkaProducer<Any, Any>(props)
    }

    return try {
      val partition = if (forcePartition >= 0) {
        val partitions = producer.partitionsFor(topic)
        if (!partitions.any { it.partition() == forcePartition }) {
          error(KafkaMessagesBundle.message("producer.wrong.partition", forcePartition, topic))
        }
        forcePartition
      }
      else
        null

      val record = ProducerRecord(topic, partition, key.value, value.value)
      headers.forEach {
        record.headers().add((it.name ?: ""), (it.value ?: "").toByteArray())
      }

      val start = System.currentTimeMillis()
      val metaInfo = producer.send(record).get(15, TimeUnit.SECONDS)
      val end = System.currentTimeMillis()
      ProducerResultMessage(key = key.text ?: "",
                            value = value.text ?: "",
                            offset = metaInfo.offset(),
                            timestamp = Date(metaInfo.timestamp()),
                            duration = (end - start).toInt(),
                            partition = metaInfo.partition())
    }
    finally {
      producer.flush()
      producer.close()
    }
  }
}