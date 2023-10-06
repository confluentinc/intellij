package com.jetbrains.bigdatatools.kafka.util

import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartType
import com.jetbrains.bigdatatools.kafka.consumer.models.ConsumerStartWith
import com.jetbrains.bigdatatools.kafka.data.KafkaDataManager
import com.jetbrains.bigdatatools.kafka.model.BdtTopicPartition
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.*

object KafkaOffsetUtils {
  suspend fun calculateOffsets(partitions: List<BdtTopicPartition>,
                               startWith: ConsumerStartWith,
                               dataManager: KafkaDataManager): Map<TopicPartition, OffsetAndMetadata> {
    val topicPartitions = partitions.map { TopicPartition(it.topic, it.partitionId) }

    val timestamp = calculateStartTime(startWith)
    if (timestamp != null) {
      return dataManager.getOffsetsForData(topicPartitions, timestamp)
    }

    val offsets = partitions.map {
      val endOffset = it.endOffset ?: error("Cannot detect latest offset")
      val startOffset = it.startOffset ?: error("Cannot detect beginning offset")
      when (startWith.type) {
        ConsumerStartType.NOW -> OffsetAndMetadata(endOffset)
        ConsumerStartType.THE_BEGINNING -> OffsetAndMetadata(startOffset)
        ConsumerStartType.LATEST_OFFSET_MINUS_X -> OffsetAndMetadata(it.endOffset - startWith.offset!!)
        ConsumerStartType.OFFSET -> OffsetAndMetadata(it.startOffset + startWith.offset!!)

        ConsumerStartType.SPECIFIC_DATE, ConsumerStartType.LAST_HOUR,
        ConsumerStartType.TODAY, ConsumerStartType.YESTERDAY -> error("Internal Error. Must be calculated for timestamp")
        ConsumerStartType.CONSUMER_GROUP -> error("Internal error. Must be not invoked")
      }
    }

    return topicPartitions.zip(offsets).toMap()
  }

  fun calculateStartTime(startWith: ConsumerStartWith): Long? {
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

}