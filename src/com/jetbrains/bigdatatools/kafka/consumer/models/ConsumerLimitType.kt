package com.jetbrains.bigdatatools.kafka.consumer.models

enum class ConsumerLimitType {
  NONE, TOPIC_NUMBER_RECORDS, DATE, TOPIC_MAX_SIZE, PARTITION_NUMBER_RECORDS, PARTITION_MAX_SIZE
}