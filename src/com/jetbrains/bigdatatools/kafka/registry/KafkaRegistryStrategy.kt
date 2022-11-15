package com.jetbrains.bigdatatools.kafka.registry

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaRegistryStrategy(val presentable: String) {
  TOPIC_NAME(KafkaMessagesBundle.message("registry.strategy.topic")),
  RECORD_NAME(KafkaMessagesBundle.message("registry.strategy.record")),
  TOPIC_RECORD_NAME(KafkaMessagesBundle.message("registry.format.topic.record")),
  CUSTOM(KafkaMessagesBundle.message("registry.strategy.custom.subject"));

  companion object {
    val producerOptions = arrayOf(TOPIC_NAME, RECORD_NAME, TOPIC_RECORD_NAME)
  }
}