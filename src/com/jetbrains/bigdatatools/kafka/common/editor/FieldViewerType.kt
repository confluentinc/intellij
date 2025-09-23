package io.confluent.kafka.common.editor

import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

/** For consumer Value and Keys fields we can have a number of options. */
enum class FieldViewerType(@Nls val title: String) {
  AUTO(KafkaMessagesBundle.message("field.viewer.type.auto")),
  TEXT(KafkaMessagesBundle.message("field.viewer.type.text")),
  JSON(KafkaMessagesBundle.message("field.viewer.type.json")),
  DECODED_BASE64(KafkaMessagesBundle.message("field.viewer.type.base64"))
}