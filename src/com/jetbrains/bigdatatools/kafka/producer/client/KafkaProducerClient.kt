package com.jetbrains.bigdatatools.kafka.producer.client

import com.jetbrains.bigdatatools.common.settings.connections.Property
import com.jetbrains.bigdatatools.common.util.withPluginClassLoader
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerProducerFieldConfig
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
import org.apache.kafka.common.serialization.Serializer
import java.util.*
import java.util.concurrent.TimeUnit

class KafkaProducerClient(val client: KafkaClient) {
  val connectionData = client.connectionData

  fun sentMessage(dataManager: KafkaDataManager,
                  topic: String,
                  key: ConsumerProducerFieldConfig, value: ConsumerProducerFieldConfig,
                  headers: List<Property> = emptyList(),
                  recordCompression: RecordCompression = RecordCompression.NONE,
                  acks: AcksType = AcksType.NONE,
                  enableIdempotence: Boolean = false,
                  forcePartition: Int = -1): ProducerResultMessage {
    val props = client.kafkaProps.clone() as Properties
    val keySerializer: Serializer<out Any> = key.type.getSerializer(dataManager, producerField = key)
    val valueSerializer = value.type.getSerializer(dataManager, producerField = value)

    props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.lowercase()
    props[AbstractKafkaSchemaSerDeConfig.CONTEXT_NAME_STRATEGY] = NullContextNameStrategy::class.java
    props[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false

    when (connectionData.registryType) {
      KafkaRegistryType.NONE -> {}
      KafkaRegistryType.CONFLUENT -> {
        connectionData.registryUrl?.let { props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = it }
      }
      KafkaRegistryType.AWS_GLUE -> {}
    }

    if (enableIdempotence)
      props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
    else
      props[ProducerConfig.ACKS_CONFIG] = acks.value.toString()

    @Suppress("UNCHECKED_CAST")
    val producer = withPluginClassLoader {
      KafkaProducer(props, keySerializer, valueSerializer) as KafkaProducer<Any, Any>
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

      val record = ProducerRecord(topic, partition, key.valueText, value.valueText)
      headers.forEach {
        record.headers().add((it.name ?: ""), (it.value ?: "").toByteArray())
      }

      val start = System.currentTimeMillis()

      @Suppress("UNCHECKED_CAST")
      val metaInfo = producer.send(record as ProducerRecord<Any, Any>).get(15, TimeUnit.SECONDS)
      val end = System.currentTimeMillis()
      ProducerResultMessage(key = key.valueText,
                            value = value.valueText,
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