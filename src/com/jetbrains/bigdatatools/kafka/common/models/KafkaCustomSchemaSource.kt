package com.jetbrains.bigdatatools.kafka.common.models

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaCustomSchemaSource(val title: String) {
  FILE(KafkaMessagesBundle.message("custom.shema.source.type.file")),
  IMPLICIT(KafkaMessagesBundle.message("custom.shema.source.type.implicit"))
}