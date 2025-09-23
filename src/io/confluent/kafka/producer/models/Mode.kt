package io.confluent.kafka.producer.models

import io.confluent.kafka.util.KafkaMessagesBundle

enum class Mode(val label: String) {
  MANUAL(KafkaMessagesBundle.message("producer.flow.mode.manual")),
  AUTO(KafkaMessagesBundle.message("producer.flow.mode.auto"))
}