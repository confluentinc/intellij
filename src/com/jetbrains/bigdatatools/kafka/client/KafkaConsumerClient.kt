package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.util.executeOnPooledThread
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import java.io.Serializable
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class KafkaConsumerClient(val client: KafkaClient) : Disposable {
  val connectionData = client.connectionData
  private val isRunning = AtomicBoolean(false)
  private var runConsumer: KafkaConsumer<Serializable, Serializable>? = null

  override fun dispose() = stop()

  fun start(topic: String, consume: (ConsumerRecord<Serializable, Serializable>) -> Unit) {
    val props = client.kafkaProps.clone() as Properties
    props[ConsumerConfig.GROUP_ID_CONFIG] = "BigDataTools"
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    val consumer = KafkaConsumer<Serializable, Serializable>(props)
    runConsumer = consumer
    consumer.subscribe(listOf(topic))
    isRunning.set(true)
    executeOnPooledThread {
      consumer.use {
        while (isRunning.get()) {
          val records = it.poll(Duration.ofMillis(500))
          records.forEach { record ->
            consume(record)
          }
        }
      }
    }
  }

  fun stop() {
    //runConsumer?.close()
    isRunning.set(false)
  }

  fun isRunning() = isRunning.get()
}