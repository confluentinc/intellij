package io.confluent.kafka.common.models

import io.confluent.kafka.util.KafkaMessagesBundle

enum class KafkaCustomSchemaSource(val title: String) {
  FILE(KafkaMessagesBundle.message("custom.shema.source.type.file")),
  IMPLICIT(KafkaMessagesBundle.message("custom.shema.source.type.implicit"))
}