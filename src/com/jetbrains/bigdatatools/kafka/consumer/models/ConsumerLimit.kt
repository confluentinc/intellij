package com.jetbrains.bigdatatools.kafka.consumer.models

class ConsumerLimit(val type: ConsumerLimitType, val value: String, time: Long?) {
  val topicRecordsCount = if (type == ConsumerLimitType.TOPIC_NUMBER_RECORDS) value.toLongOrNull() else null
  val topicRecordsSize = if (type == ConsumerLimitType.TOPIC_MAX_SIZE) value.toLongOrNull() else null
  val partitionRecordsCount = if (type == ConsumerLimitType.PARTITION_NUMBER_RECORDS) value.toLongOrNull() else null
  val partitionRecordsSize = if (type == ConsumerLimitType.PARTITION_MAX_SIZE) value.toLongOrNull() else null
  val time = if (type == ConsumerLimitType.DATE) time else null
}