package io.confluent.intellijplugin.util

import io.confluent.intellijplugin.consumer.models.ConsumerStartType
import io.confluent.intellijplugin.consumer.models.ConsumerStartWith
import io.confluent.intellijplugin.data.KafkaDataManager
import io.confluent.intellijplugin.model.BdtTopicPartition
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import java.util.*

object KafkaOffsetUtils {
    suspend fun calculateOffsets(
        partitions: List<BdtTopicPartition>,
        startWith: ConsumerStartWith,
        dataManager: KafkaDataManager
    ): Map<TopicPartition, OffsetAndMetadata> {
        val topicPartitions: Map<TopicPartition, BdtTopicPartition> =
            partitions.associateBy { TopicPartition(it.topic, it.partitionId) }

        val timestamp = calculateStartTime(startWith)
        if (timestamp != null) {
            val offsetsForData = dataManager.getOffsetsForData(topicPartitions.keys, timestamp)
            val res = offsetsForData.entries.map {
                if (it.value > -1) {
                    return@map it.key to OffsetAndMetadata(it.value)
                } else {
                    return@map it.key to OffsetAndMetadata(topicPartitions[it.key]!!.startOffset!!)
                }
            }.toMap()
            return res
        }

        val offsets = partitions.map {
            val endOffset = it.endOffset ?: error("Cannot detect latest offset")
            val startOffset = it.startOffset ?: error("Cannot detect beginning offset")

            when (startWith.type) {
                ConsumerStartType.NOW -> OffsetAndMetadata(endOffset)
                ConsumerStartType.THE_BEGINNING -> OffsetAndMetadata(startOffset)
                ConsumerStartType.LATEST_OFFSET_MINUS_X -> {
                    val newOffset = endOffset - startWith.offset!!
                    if (newOffset < startOffset) {
                        throw KafkaOffsetException(
                            KafkaMessagesBundle.message(
                                "exception.message.kafka.change.offset.min.offset.limit",
                                it.topic, it.partitionId, newOffset, endOffset
                            )
                        )
                    }
                    OffsetAndMetadata(newOffset)
                }

                ConsumerStartType.OFFSET -> {
                    val newOffset = startOffset + startWith.offset!!
                    if (newOffset > endOffset) {
                        throw KafkaOffsetException(
                            KafkaMessagesBundle.message(
                                "exception.message.kafka.change.offset.max.offset.limit",
                                it.topic, it.partitionId, newOffset, endOffset
                            )
                        )
                    }
                    OffsetAndMetadata(newOffset)
                }

                ConsumerStartType.SPECIFIC_DATE, ConsumerStartType.LAST_HOUR,
                ConsumerStartType.TODAY, ConsumerStartType.YESTERDAY -> error("Internal Error. Must be calculated for timestamp")

                ConsumerStartType.CONSUMER_GROUP -> error("Internal error. Must be not invoked")
            }
        }

        return topicPartitions.keys.zip(offsets).toMap()
    }

    fun calculateStartTime(startWith: ConsumerStartWith): Long? {
        val calendar = Calendar.getInstance()
        calendar.time = Date()

        return when (startWith.type) {
            ConsumerStartType.NOW -> null
            ConsumerStartType.LAST_HOUR -> {
                calendar.add(Calendar.HOUR_OF_DAY, -1)
                calendar.time.time
            }

            ConsumerStartType.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.time.time
            }

            ConsumerStartType.YESTERDAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.add(Calendar.DAY_OF_YEAR, -1)
                calendar.time.time
            }

            ConsumerStartType.SPECIFIC_DATE -> startWith.time
            else -> null
        }
    }
}

