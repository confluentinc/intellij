package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.common.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaPropertySource(override val title: String) : RenderableEntity {
  DIRECT(MessagesBundle.message("settings.property.source.direct")),
  FILE(KafkaMessagesBundle.message("settings.property.source.file"));

  override val id = name.lowercase()
}
