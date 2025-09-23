package io.confluent.kafka.rfs

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle

enum class SchemaRegistryAuthType(override val title: String) : RenderableEntity {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  BASIC_AUTH(KafkaMessagesBundle.message("kafka.auth.basic")),
  BEARER(KafkaMessagesBundle.message("kafka.auth.bearer"));

  override val id = name
}
