package com.jetbrains.bigdatatools.kafka.rfs

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

enum class KafkaAuthMethod(val title: String) {
  NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
  SASL(KafkaMessagesBundle.message("kafka.auth.sasls")),
  SSL(KafkaMessagesBundle.message("kafka.auth.ssl")),
  AWS_IAM(KafkaMessagesBundle.message("kafka.auth.aws_iam")),
}
