package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class SchemaRegistryAuthType(val title: String) {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  BASIC_AUTH(KafkaMessagesBundle.message("kafka.auth.basic")),
  BEARER(KafkaMessagesBundle.message("kafka.auth.bearer"));
}
