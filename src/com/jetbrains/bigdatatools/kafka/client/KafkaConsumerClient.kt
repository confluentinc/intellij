package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.util.executeOnPooledThread
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
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

  fun start(topic: String,
            startOffset: Long? = null,
            startTimeMs: Long? = null,
            partitionFilter: List<Int>? = null,
            consume: (ConsumerRecord<Serializable, Serializable>) -> Unit) {
    val props = client.kafkaProps.clone() as Properties
    props[ConsumerConfig.GROUP_ID_CONFIG] = "BigDataTools" + UUID.randomUUID()
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    val consumer = KafkaConsumer<Serializable, Serializable>(props)
    runConsumer = consumer


    val partitions = consumer.partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) }.toMutableList()
    partitionFilter?.forEach {
      partitions.removeAt(it)
    }

    consumer.assign(partitions)

    val startFromOffsetSeek = startOffset?.let { partitionOffsetsForStartOffset(consumer, partitions, it) }
    val startFromDateSeek = startTimeMs?.let { partitionOffsetsForStartDate(it, partitions, consumer) }
    val startFromSeek = startFromOffsetSeek ?: startFromDateSeek
    startFromSeek?.forEach {
      it.value?.let { offset -> consumer.seek(it.key, offset) }
    }

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

  private fun partitionOffsetsForStartOffset(consumer: KafkaConsumer<Serializable, Serializable>,
                                             partitions: MutableList<TopicPartition>,
                                             offset: Long) = if (offset <= 0) {
    consumer.endOffsets(partitions).map { it.key to (it.value + offset).coerceAtLeast(0) }.toMap()
  }
  else {
    consumer.beginningOffsets(partitions).map { it.key to (it.value + offset) }.toMap()
  }


  fun stop() {
    //runConsumer?.close()
    isRunning.set(false)
  }

  fun isRunning() = isRunning.get()

  private fun partitionOffsetsForStartDate(startTime: Long,
                                           partitions: MutableList<TopicPartition>,
                                           consumer: KafkaConsumer<Serializable, Serializable>): Map<TopicPartition, Long?>? {
    val timestampsToSearch = partitions.associateWith { startTime }
    val offsetsForTimes = consumer.offsetsForTimes(timestampsToSearch)
    return offsetsForTimes?.map { it.key to it.value?.offset() }?.toMap()
  }
}

