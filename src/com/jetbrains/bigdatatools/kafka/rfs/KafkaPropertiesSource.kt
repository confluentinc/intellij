package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.common.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaPropertiesSource(override val title: String) : RenderableEntity {
  FIELD(KafkaMessagesBundle.message("kafka.property.source.field")),
  FILE(KafkaMessagesBundle.message("kafka.property.source.file"));

  override val id = name
}