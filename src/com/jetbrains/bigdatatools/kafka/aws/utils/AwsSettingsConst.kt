package com.jetbrains.bigdatatools.kafka.aws.utils

import com.jetbrains.bigdatatools.kafka.core.settings.ModificationKey
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

object AwsSettingsConst {
  val S3_ACCESS_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.access.key"))
  val S3_SECRET_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.secret.key"))
}