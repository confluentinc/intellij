package com.jetbrains.bigdatatools.kafka.producer.client

import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.common.models.ProducerField
import com.jetbrains.bigdatatools.kafka.producer.models.AcksType
import com.jetbrains.bigdatatools.kafka.producer.models.ProducerResultMessage
import com.jetbrains.bigdatatools.kafka.producer.models.RecordCompression
import com.jetbrains.bigdatatools.settings.connections.Property
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.*

class KafkaProducerClient(val client: KafkaClient) {
  val connectionData = client.connectionData

  fun sentMessage(topic: String, key: ProducerField, value: ProducerField,
                  headers: List<Property> = emptyList(),
                  recordCompression: RecordCompression = RecordCompression.NONE,
                  acks: AcksType = AcksType.NONE,
                  enableIdempotence: Boolean = false,
                  forcePartition: Int = -1): ProducerResultMessage {
    val props = client.kafkaProps.clone() as Properties
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = key.type.getSerializer()::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = value.type.getSerializer()::class.java
    props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.lowercase()

    if (enableIdempotence)
      props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = true
    else
      props[ProducerConfig.ACKS_CONFIG] = acks.value.toString()

    val producer = KafkaProducer<Any, Any>(props)

    return try {
      val record = ProducerRecord(topic, if (forcePartition >= 0) forcePartition else null, key.value, value.value)
      headers.forEach {
        record.headers().add((it.name ?: ""), (it.value ?: "").toByteArray())
      }

      val start = System.currentTimeMillis()
      val metaInfo = producer.send(record).get()
      val end = System.currentTimeMillis()
      ProducerResultMessage(key = key.value?.toString() ?: "",
        value = value.value?.toString() ?: "",
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