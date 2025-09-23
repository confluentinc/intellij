package io.confluent.kafka.rfs

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle

// The name does not reflect the meaning. This is only the connection settings type "user defined in UI" vs "taken from properties file".
enum class KafkaPropertySource(override val title: String) : RenderableEntity {
  DIRECT(KafkaMessagesBundle.message("kafka.property.source.field")),
  FILE(KafkaMessagesBundle.message("kafka.property.source.file"));

  override val id = name.lowercase()
}
