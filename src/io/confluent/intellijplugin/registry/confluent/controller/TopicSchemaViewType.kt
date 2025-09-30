package io.confluent.intellijplugin.registry.confluent.controller

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class TopicSchemaViewType(@Nls val title: String, val suffix: String) {
  DISABLED("", ""),
  KEY(KafkaMessagesBundle.message("topic.schema.view.type.key"), "-key"),
  VALUE(KafkaMessagesBundle.message("topic.schema.view.type.value"), "-value"),
  TOPIC(KafkaMessagesBundle.message("topic.schema.view.type.topic"), "")
}