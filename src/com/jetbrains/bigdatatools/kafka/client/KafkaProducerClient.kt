package com.jetbrains.bigdatatools.kafka.client

import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.serialization.StringSerializer
import java.util.*

class KafkaProducerClient(val client: KafkaClient) {
  private var producer: KafkaProducer<*, *>? = null

  fun sentMessage(topic: String, key: String, value: String): RecordMetadata {
    val producer = createProducer()
    return try {
      val record = ProducerRecord(topic, key, value)
      producer.send(record).get()
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
    props[ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
    props[ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG] = StringSerializer::class.java.name
    return KafkaProducer(props)
  }


}