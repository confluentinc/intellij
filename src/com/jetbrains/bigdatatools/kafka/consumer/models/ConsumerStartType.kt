package com.jetbrains.bigdatatools.kafka.consumer.models

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class ConsumerStartType(val title: String) {
  NOW(KafkaMessagesBundle.message("consumer.start.type.now")),
  THE_BEGINNING(KafkaMessagesBundle.message("consumer.start.type.beginning")),
  LAST_HOUR(KafkaMessagesBundle.message("consumer.start.type.lastHour")),
  TODAY(KafkaMessagesBundle.message("consumer.start.type.today")),
  YESTERDAY(KafkaMessagesBundle.message("consumer.start.type.yesterday")),
  SPECIFIC_DATE(KafkaMessagesBundle.message("consumer.start.type.specificDate")),
  CONSUMER_GROUP(KafkaMessagesBundle.message("consumer.start.type.consumerGroup")),
  LATEST_OFFSET_MINUS_X(KafkaMessagesBundle.message("consumer.start.type.latestOffsetMinusX")),
  OFFSET(KafkaMessagesBundle.message("consumer.start.type.offset"))
}