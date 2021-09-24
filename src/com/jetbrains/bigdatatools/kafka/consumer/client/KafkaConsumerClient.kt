package com.jetbrains.bigdatatools.kafka.consumer.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import com.jetbrains.bigdatatools.kafka.consumer.models.RunConsumerConfig
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

class KafkaConsumerClient(val client: KafkaClient,
                          val onStop: () -> Unit) : Disposable {
  val connectionData = client.connectionData
  private val isRunning = AtomicBoolean(false)
  private var runConsumer: KafkaConsumer<Serializable, Serializable>? = null

  override fun dispose() = stop()

  fun start(config: RunConsumerConfig,
            consume: (ConsumerRecord<Serializable, Serializable>) -> Unit) {
    val consumer = createConsumer()
    runConsumer = consumer

    val partitions = calculatePartitions(consumer, config.topic, config.partitions)
    consumer.assign(partitions)
    seekPartitions(consumer, partitions, config.startWith)

    isRunning.set(true)

    executeOnPooledThread {
      try {
        @Suppress("CanBeVal")
        var needToReadTopicCount = config.limit.topicRecordsCount
        val needToReadPartitionCount = config.limit.partitionRecordsCount?.let { limit ->
          partitions.associate { it.partition() to limit }.toMutableMap()
        }

        var needToReadTopicSize = config.limit.topicRecordsSize
        val needToReadPartitionSize = config.limit.partitionRecordsSize?.let { limit ->
          partitions.associate { it.partition() to limit }.toMutableMap()
        }

        consumer.use { consumer ->
          while (isRunning.get()) {
            val records = consumer.poll(Duration.ofMillis(500))

            records.forEach { record ->
              if (config.limit.time != null && record.timestamp() > config.limit.time) {
                return@executeOnPooledThread
              }

              if (!config.filter.isRecordPassFilter(record))
                return@forEach

              val recordSize = record.serializedValueSize() + record.serializedKeySize()

              if (needToReadTopicSize != null && needToReadTopicSize!! <= 0L) {
                return@executeOnPooledThread
              }
              needToReadTopicSize = needToReadTopicSize?.minus(recordSize)

              if (needToReadPartitionSize != null) {
                if (needToReadPartitionSize.isEmpty()) {
                  return@executeOnPooledThread
                }

                val left = needToReadPartitionSize[record.partition()]
                when {
                  left == null -> return@forEach
                  left > 0 -> needToReadPartitionSize[record.partition()] = left - 1
                  else -> needToReadPartitionSize.remove(record.partition())
                }
              }


              if (needToReadTopicCount == 0L) {
                return@executeOnPooledThread
              }
              needToReadTopicCount = needToReadTopicCount?.minus(1)

              if (needToReadPartitionCount != null) {
                if (needToReadPartitionCount.isEmpty()) {
                  return@executeOnPooledThread
                }

                val left = needToReadPartitionCount[record.partition()]
                when {
                  left == null -> return@forEach
                  left > 1 -> needToReadPartitionCount[record.partition()] = left - 1
                  else -> needToReadPartitionCount.remove(record.partition())
                }
              }

              consume(record)
            }
          }
        }
      }
      finally {
        stop()
        onStop()
      }
    }
  }

  private fun seekPartitions(consumer: KafkaConsumer<Serializable, Serializable>,
                             partitions: List<TopicPartition>,
                             startWith: ConsumerStartWith) {
    val startFromOffsetSeek = startWith.offset?.let { partitionOffsetsForStartOffset(consumer, partitions, it) }
    val startFromDateSeek = startWith.time?.let { partitionOffsetsForStartDate(it, partitions, consumer) }
    val startFromSeek = startFromOffsetSeek ?: startFromDateSeek
    startFromSeek?.forEach {
      it.value?.let { offset -> consumer.seek(it.key, offset) }
    }
  }

  private fun createConsumer(): KafkaConsumer<Serializable, Serializable> {
    val props = client.kafkaProps.clone() as Properties
    props[ConsumerConfig.GROUP_ID_CONFIG] = "BigDataTools" + UUID.randomUUID()
    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = StringDeserializer::class.java
    val consumer = KafkaConsumer<Serializable, Serializable>(props)
    return consumer
  }

  private fun calculatePartitions(consumer: KafkaConsumer<Serializable, Serializable>,
                                  topic: String,
                                  partitionFilter: List<Int>?): List<TopicPartition> {
    var partitions = consumer.partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) }

    if (partitionFilter != null) {
      val allPartitions = partitions.map { it.partition() }.toSet()
      partitions = partitionFilter.toSet().intersect(allPartitions).map { TopicPartition(topic, it) }
    }
    return partitions
  }

  fun stop() {
    //runConsumer?.close()
    isRunning.set(false)
  }

  fun isRunning() = isRunning.get()

  private fun partitionOffsetsForStartOffset(consumer: KafkaConsumer<Serializable, Serializable>,
                                             partitions: List<TopicPartition>,
                                             offset: Long) = if (offset < 0) {
    consumer.endOffsets(partitions).map { it.key to (it.value + offset).coerceAtLeast(0) }.toMap()
  }
  else {
    consumer.beginningOffsets(partitions).map { it.key to (it.value + offset) }.toMap()
  }

  private fun partitionOffsetsForStartDate(startTime: Long,
                                           partitions: List<TopicPartition>,
                                           consumer: KafkaConsumer<Serializable, Serializable>): Map<TopicPartition, Long?>? {
    val timestampsToSearch = partitions.associateWith { startTime }
    val offsetsForTimes = consumer.offsetsForTimes(timestampsToSearch)
    return offsetsForTimes?.map { it.key to it.value?.offset() }?.toMap()
  }
}

