package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.core.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class SchemaRegistryAuthType(override val title: String) : RenderableEntity {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  BASIC_AUTH(KafkaMessagesBundle.message("kafka.auth.basic")),
  BEARER(KafkaMessagesBundle.message("kafka.auth.bearer"));

  override val id = name
}
