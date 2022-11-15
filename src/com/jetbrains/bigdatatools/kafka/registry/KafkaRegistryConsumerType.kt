package com.jetbrains.bigdatatools.kafka.registry

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaRegistryConsumerType(val presentable: String) {
  AUTO(KafkaMessagesBundle.message("registry.consumer.type.auto")),
  SUBJECT(KafkaMessagesBundle.message("registry.consumer.type.subject")),
  SCHEMA_ID(KafkaMessagesBundle.message("registry.consumer.type.schema.id")),
  CUSTOM(KafkaMessagesBundle.message("registry.consumer.type.custom"));
}