package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaPropertySource(val title: String) {
  DIRECT(KafkaMessagesBundle.message("settings.property.source.direct")),
  FILE(KafkaMessagesBundle.message("settings.property.source.file"))
}
