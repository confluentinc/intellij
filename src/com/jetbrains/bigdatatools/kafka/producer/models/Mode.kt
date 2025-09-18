package com.jetbrains.bigdatatools.kafka.producer.models

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class Mode(val label: String) {
  MANUAL(KafkaMessagesBundle.message("producer.flow.mode.manual")),
  AUTO(KafkaMessagesBundle.message("producer.flow.mode.auto"))
}