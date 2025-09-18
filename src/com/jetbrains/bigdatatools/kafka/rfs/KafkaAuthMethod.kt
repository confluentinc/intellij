package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.core.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaAuthMethod(override val title: String) : RenderableEntity {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  SASL(KafkaMessagesBundle.message("kafka.auth.sasls")),
  SSL(KafkaMessagesBundle.message("kafka.auth.ssl")),
  AWS_IAM(KafkaMessagesBundle.message("kafka.auth.aws_iam"));

  override val id = name
}
