package io.confluent.kafka.aws.utils

import io.confluent.kafka.core.settings.ModificationKey
import io.confluent.kafka.util.KafkaMessagesBundle

object AwsSettingsConst {
  val S3_ACCESS_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.access.key"))
  val S3_SECRET_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.secret.key"))
}