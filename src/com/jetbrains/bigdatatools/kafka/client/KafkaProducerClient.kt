package com.jetbrains.bigdatatools.kafka.client

import com.jetbrains.bigdatatools.kafka.ui.ProducerResultMessage
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

class KafkaProducerClient(val client: KafkaClient) {
  fun sentMessage(topic: String, key: String, value: String): ProducerResultMessage {
    val producer = createProducer()
    return try {
      val record = ProducerRecord(topic, key, value)
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

  private fun createProducer(): KafkaProducer<String, String> {
    val props = client.kafkaProps.clone() as Properties
    //props[ProducerConfig.BOOTSTRAP_SERVERS_CONFIG] = BOOTSTRAP_SERVERS
    props[ProducerConfig.CLIENT_ID_CONFIG] = "KafkaExampleProducer"
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java
    return KafkaProducer(props)
  }


}