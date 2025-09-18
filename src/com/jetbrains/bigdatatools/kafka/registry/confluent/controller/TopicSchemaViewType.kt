package com.jetbrains.bigdatatools.kafka.registry.confluent.controller

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class TopicSchemaViewType(@Nls val title: String, val suffix: String) {
  DISABLED("", ""),
  KEY(KafkaMessagesBundle.message("topic.schema.view.type.key"), "-key"),
  VALUE(KafkaMessagesBundle.message("topic.schema.view.type.value"), "-value"),
  TOPIC(KafkaMessagesBundle.message("topic.schema.view.type.topic"), "")
}