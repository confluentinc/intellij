package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.ui.FieldType
import com.jetbrains.bigdatatools.kafka.ui.KafkaField
import com.jetbrains.bigdatatools.kafka.ui.ProducerResultMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.*
import java.io.Serializable
import java.util.*

class KafkaProducerClient(val client: KafkaClient) {
  fun sentMessage(topic: String, key: KafkaField, value: KafkaField): ProducerResultMessage {
    val props = client.kafkaProps.clone() as Properties
    props[ProducerConfig.CLIENT_ID_CONFIG] = "KafkaExampleProducer"
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = chooseSerializer(key.type)::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = chooseSerializer(value.type)::class.java
    val producer = KafkaProducer<Serializable, Serializable>(props)

    return try {
      val record = ProducerRecord(topic, key.value, value.value)
      val start = System.currentTimeMillis()
      val metaInfo = producer.send(record).get()
      val end = System.currentTimeMillis()
      ProducerResultMessage(text = "",
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