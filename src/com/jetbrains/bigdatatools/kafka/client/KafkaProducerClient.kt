package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.ui.*
import com.jetbrains.bigdatatools.settings.connections.Property
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.*
import java.io.Serializable
import java.util.*

class KafkaProducerClient(val client: KafkaClient) {
  val connectionData = client.connectionData

  fun sentMessage(topic: String, key: KafkaField, value: KafkaField,
                  headers: List<Property> = emptyList(),
                  recordCompression: RecordCompression = RecordCompression.NONE,
                  acks: AcksType = AcksType.NONE,
                  enableIdempotence: Boolean = false,
                  forcePartition: Int = -1): ProducerResultMessage {
    val props = client.kafkaProps.clone() as Properties
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = chooseSerializer(key.type)::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = chooseSerializer(value.type)::class.java
    props[ProducerConfig.COMPRESSION_TYPE_CONFIG] = recordCompression.name.toLowerCase()

    if (enableIdempotence) {
      props[ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG] = enableIdempotence
    }
    else {
      props[ProducerConfig.ACKS_CONFIG] = acks.value.toString()
    }

    val producer = KafkaProducer<Serializable, Serializable>(props)

    return try {
      val record = ProducerRecord(topic, if (forcePartition >= 0) forcePartition else null, key.value, value.value)
      headers.forEach {
        record.headers().add(it.name, it.value.toByteArray())
      }

      record.partition()

      val start = System.currentTimeMillis()
      val metaInfo = producer.send(record).get()
      val end = System.currentTimeMillis()
      ProducerResultMessage(key = "",
                            value = "",
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

  private fun chooseSerializer(type: FieldType) = when (type) {
    FieldType.STRING -> StringSerializer()
    FieldType.JSON -> StringSerializer()
    FieldType.LONG -> LongSerializer()
    FieldType.DOUBLE -> DoubleSerializer()
    FieldType.FLOAT -> FloatSerializer()
    FieldType.BASE64 -> BytesSerializer()
    FieldType.NULL -> VoidSerializer()
  }
}