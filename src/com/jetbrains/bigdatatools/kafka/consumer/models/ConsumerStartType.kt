package com.jetbrains.bigdatatools.kafka.consumer.models

enum class ConsumerStartType {
  NOW, LAST_HOUR, TODAY, YESTERDAY, SPECIFIC_DATE,
  THE_BEGINNING, CONSUMER_GROUP, LATEST_OFFSET_MINUS_X, OFFSET
}