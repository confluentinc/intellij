package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.components.RenderableEntity

enum class KafkaPropertySource(override val title: String) : RenderableEntity {
  DIRECT(KafkaMessagesBundle.message("settings.property.source.direct")),
  FILE(KafkaMessagesBundle.message("settings.property.source.file"));

  override val id = name.lowercase()
}
