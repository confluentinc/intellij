package com.jetbrains.bigdatatools.kafka.registry

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaRegistryKeyValue(val presentable: String) {
  KEY(KafkaMessagesBundle.message("registry.key")),
  VALUE(KafkaMessagesBundle.message("registry.value"));
}