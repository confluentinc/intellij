package io.confluent.kafka.rfs

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle

// The name does not reflect the meaning. This is only the connection settings type "user defined in UI" vs "taken from properties file".
enum class KafkaConfigurationSource(override val title: String) : RenderableEntity {
  CLOUD(KafkaMessagesBundle.message("settings.broker.type.cloud")),
  FROM_UI(KafkaMessagesBundle.message("settings.property.source.direct")),
  FROM_PROPERTIES(KafkaMessagesBundle.message("settings.property.source.file"));

  override val id = name.lowercase()
}