package com.jetbrains.bigdatatools.kafka.consumer.client

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.common.models.FieldType
import com.jetbrains.bigdatatools.kafka.common.settings.StorageConsumerConfig
import com.jetbrains.bigdatatools.kafka.consumer.editor.ConsumerEditorUtils
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryConsumerType
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryUtil.schemaType
import com.jetbrains.bigdatatools.kafka.statistics.KafkaUsagesCollector
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.errors.SerializationException
import java.time.Duration
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class KafkaConsumerClient(val dataManager: KafkaDataManager,
                          val onStart: () -> Unit,
                          val onStop: () -> Unit) : Disposable {
  val client = dataManager.client
  val connectionData = client.connectionData
  private val isRunning = AtomicBoolean(false)
  private var curRunId = AtomicInteger(0)
  private var runConsumer: KafkaConsumer<Any, Any>? = null

  override fun dispose() = stop()

  fun start(config: StorageConsumerConfig,
            consume: (ConsumerRecord<Any, Any>) -> Unit,
            consumeError: (Throwable) -> Unit) {
    isRunning.set(true)
    onStart()

    if (config.topic.isNullOrBlank()) {
      error(KafkaMessagesBundle.message("consumer.error.topic.empty"))
    }

    val consumer = createConsumer(config)
    runConsumer = consumer

    val parsedPartitionFilter = ConsumerEditorUtils.parsePartitionsText(config.partitions).ifEmpty { null }
    val partitions = calculatePartitions(consumer, config.getInnerTopic(), parsedPartitionFilter)
    if (partitions.isEmpty()) {
      error(KafkaMessagesBundle.message("consumer.partition.not.found", config.getInnerTopic()))
    }
    consumer.assign(partitions)
    seekPartitions(consumer, partitions, config.getStartsWith())

    val taskRunId = curRunId.incrementAndGet()

    try {

      val limit = config.getLimit()

      var needToReadTopicCount = limit.topicRecordsCount
      val needToReadPartitionCount = limit.partitionRecordsCount?.let { count ->
        partitions.associate { it.partition() to count }.toMutableMap()
      }

      var needToReadTopicSize = limit.topicRecordsSize
      val needToReadPartitionSize = limit.partitionRecordsSize?.let { size ->
        partitions.associate { it.partition() to size }.toMutableMap()
      }

      consumer.use { kafkaConsumer ->
        while (isRunning.get()) {
          if (curRunId.get() != taskRunId)
            return

          val records = try {
            kafkaConsumer.poll(Duration.ofMillis(2000))
          }
          catch (t: SerializationException) {
            val shortMessage = t.message?.removePrefix("Error deserializing key/value for partition ")
                                 ?.removeSuffix(". If needed, please seek past the record to continue consumption.") ?: ""
            val offset = shortMessage.substringAfterLast(" ", "").toLongOrNull()
            val topicPartitionPart = shortMessage.substringBeforeLast(" at offset", "")
            val topic = topicPartitionPart.substringBeforeLast("-").ifBlank { null }
            val partition = topicPartitionPart.substringAfterLast("-").toIntOrNull()
            if (offset == null || topic == null || partition == null) {
              consumeError(t)
              return
            }
            kafkaConsumer.seek(TopicPartition(topic, partition), offset + 1)
            consumeError(t)
            emptyList()
          }
          catch (t: Throwable) {
            consumeError(t)
            return
          }

          var consumedRecords = 0
          records.forEach { record: ConsumerRecord<Any, Any> ->
            if (limit.time != null && record.timestamp() > limit.time) {
              return
            }

            if (!config.getFilter().isRecordPassFilter(record))
              return@forEach

            val recordSize = record.serializedValueSize() + record.serializedKeySize()

            if (needToReadTopicSize != null && needToReadTopicSize!! <= 0L) {
              return
            }
            needToReadTopicSize = needToReadTopicSize?.minus(recordSize)

            if (needToReadPartitionSize != null) {
              if (needToReadPartitionSize.isEmpty()) {
                return
              }

              val left = needToReadPartitionSize[record.partition()]
              when {
                left == null -> return@forEach
                left > 0 -> needToReadPartitionSize[record.partition()] = left - 1
                else -> needToReadPartitionSize.remove(record.partition())
              }
            }

            if (needToReadTopicCount == 0L) {
              return
            }
            needToReadTopicCount = needToReadTopicCount?.minus(1)

            if (needToReadPartitionCount != null) {
              if (needToReadPartitionCount.isEmpty()) {
                return
              }

              val left = needToReadPartitionCount[record.partition()]
              when {
                left == null -> return@forEach
                left > 1 -> needToReadPartitionCount[record.partition()] = left - 1
                else -> needToReadPartitionCount.remove(record.partition())
              }
            }

            consume(record)
            consumedRecords++
          }

          if (consumedRecords > 0) {
            KafkaUsagesCollector.consumedKeyValue.log(config.getKeyType(), config.getValueType(), consumedRecords)
          }
        }
      }
    }
    finally {
      stop()
    }
  }

  private fun seekPartitions(consumer: KafkaConsumer<Any, Any>,
                             partitions: List<TopicPartition>,
                             startWith: ConsumerStartWith) {
    val startFromOffsetSeek = startWith.offset?.let { partitionOffsetsForStartOffset(consumer, partitions, it) }

    val startTime = calculateStartTime(startWith)
    val startFromDateSeek: Map<TopicPartition, Long?>? = startTime?.let { partitionOffsetsForStartDate(it, partitions, consumer) }

    val startFromConsumerGroupSeek = startWith.consumerGroup?.let { consumerGroupId ->
      val offsets: Map<TopicPartition, OffsetAndMetadata> = client.getConsumerGroupOffsets(consumerGroupId)
      offsets.filter { it.key in partitions }.map { it.key to it.value.offset() }.toMap()
    }
    val startFromSeek = startFromOffsetSeek ?: startFromDateSeek ?: startFromConsumerGroupSeek
    startFromSeek?.forEach {
      it.value?.let { offset -> consumer.seek(it.key, offset) }
    }
  }

  private fun createConsumer(config: StorageConsumerConfig): KafkaConsumer<Any, Any> {
    val props = client.kafkaProps.clone() as Properties

    props[ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG] = config.getKeyType().getDeserializationClass()::class.java
    props[ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG] = config.getValueType().getDeserializationClass()::class.java
    props[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG] = connectionData.registryUrl


    getSchema(config, isKey = true)?.let { props[FieldType.KEY_PARSED_SCHEMA_CONFIG_KEY] = it }
    getSchema(config, isKey = false)?.let { props[FieldType.VALUE_PARSED_SCHEMA_CONFIG_KEY] = it }

    config.properties.forEach {
      it.value.toIntOrNull()?.let { value -> props[it.key] = value }
    }
    return KafkaConsumer(props)
  }

  private fun calculatePartitions(consumer: KafkaConsumer<Any, Any>,
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
    isRunning.set(false)
    onStop()
  }

  fun isRunning() = isRunning.get()

  private fun partitionOffsetsForStartOffset(consumer: KafkaConsumer<Any, Any>,
                                             partitions: List<TopicPartition>,
                                             offset: Long) = if (offset < 0) {
    consumer.endOffsets(partitions).map { it.key to (it.value + offset).coerceAtLeast(0) }.toMap()
  }
  else {
    consumer.beginningOffsets(partitions).map { it.key to (it.value + offset) }.toMap()
  }

  private fun partitionOffsetsForStartDate(startTime: Long,
                                           partitions: List<TopicPartition>,
                                           consumer: KafkaConsumer<Any, Any>): Map<TopicPartition, Long?>? {
    val timestampsToSearch = partitions.associateWith { startTime }
    val offsetsForTimes = consumer.offsetsForTimes(timestampsToSearch)
    return offsetsForTimes?.map { it.key to it.value?.offset() }?.toMap()
  }

  private fun calculateStartTime(startWith: ConsumerStartWith): Long? {
    val calendar = Calendar.getInstance()
    calendar.time = Date()

    startWith.time?.let {
      return it
    }

    return when (startWith.type) {
      ConsumerStartType.NOW -> null
      ConsumerStartType.LAST_HOUR -> {
        calendar.add(Calendar.HOUR_OF_DAY, -1)
        calendar.time
      }
      ConsumerStartType.TODAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.time
      }
      ConsumerStartType.YESTERDAY -> {
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        calendar.time
      }
      ConsumerStartType.THE_BEGINNING -> {
        Date(0)
      }
      else -> null
    }?.time
  }

  private fun getSchema(config: StorageConsumerConfig, isKey: Boolean): ParsedSchema? {
    val fieldType = if (isKey) config.getKeyType() else config.getValueType()
    val registryType = if (isKey) config.getKeyRegistryType() else config.getValueRegistryType()
    val subject = if (isKey) config.keySubject else config.valueSubject
    val schemaId = if (isKey) config.keySchemaId else config.valueSchemaId
    val schemaJson = if (isKey) config.keyCustomSchema else config.valueCustomSchema

    if (fieldType !in FieldType.registryValues)
      return null
    return when (registryType) {
      KafkaRegistryConsumerType.AUTO -> null
      KafkaRegistryConsumerType.SUBJECT -> dataManager.getRegistrySchema(subject)?.meta?.let {
        KafkaRegistryUtil.parseSchema(it)
      }
      KafkaRegistryConsumerType.SCHEMA_ID -> {
        val id = schemaId.toIntOrNull()
        dataManager.getRegistrySchemaById(id)
      }
      KafkaRegistryConsumerType.CUSTOM -> {
        KafkaRegistryUtil.parseSchema(fieldType.schemaType, schemaJson)
      }
    }
  }
}