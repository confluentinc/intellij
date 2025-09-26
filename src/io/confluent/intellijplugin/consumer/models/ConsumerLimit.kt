package io.confluent.intellijplugin.consumer.models

data class ConsumerLimit(val type: ConsumerLimitType, val value: String, val time: Long?) {
  val topicRecordsCount = if (type == ConsumerLimitType.TOPIC_NUMBER_RECORDS) value.toLongOrNull() else null
  val topicRecordsSize = if (type == ConsumerLimitType.TOPIC_MAX_SIZE) value.toLongOrNull() else null
  val partitionRecordsCount = if (type == ConsumerLimitType.PARTITION_NUMBER_RECORDS) value.toLongOrNull() else null
  val partitionRecordsSize = if (type == ConsumerLimitType.PARTITION_MAX_SIZE) value.toLongOrNull() else null
}