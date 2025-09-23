package io.confluent.kafka.registry

import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryKeyValue(@Nls val presentable: String) {
  KEY(KafkaMessagesBundle.message("registry.key")),
  VALUE(KafkaMessagesBundle.message("registry.value"));
}