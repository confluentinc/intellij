package io.confluent.kafka.rfs

import io.confluent.kafka.core.settings.components.RenderableEntity
import io.confluent.kafka.util.KafkaMessagesBundle

enum class KafkaAuthMethod(override val title: String) : RenderableEntity {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  SASL(KafkaMessagesBundle.message("kafka.auth.sasls")),
  SSL(KafkaMessagesBundle.message("kafka.auth.ssl")),
  AWS_IAM(KafkaMessagesBundle.message("kafka.auth.aws_iam"));

  override val id = name
}
