package io.confluent.kafka.rfs

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle

enum class KafkaPropertiesSource(override val title: String) : RenderableEntity {
  FIELD(KafkaMessagesBundle.message("kafka.property.source.field")),
  FILE(KafkaMessagesBundle.message("kafka.property.source.file"));

  override val id = name
}