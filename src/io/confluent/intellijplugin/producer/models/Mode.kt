package io.confluent.intellijplugin.producer.models

import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class Mode(val label: String) {
  MANUAL(KafkaMessagesBundle.message("producer.flow.mode.manual")),
  AUTO(KafkaMessagesBundle.message("producer.flow.mode.auto"))
}