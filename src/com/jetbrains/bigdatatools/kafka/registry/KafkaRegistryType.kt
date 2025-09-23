package io.confluent.kafka.registry

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryType(override val id: String, @Nls override val title: String) : RenderableEntity {
  NONE("none", KafkaMessagesBundle.message("schema.type.none")),
  CONFLUENT("confluent", KafkaMessagesBundle.message("schema.type.confluent")),
  AWS_GLUE("glue", KafkaMessagesBundle.message("schema.type.glue"));
}