package io.confluent.intellijplugin.aws.utils

import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.util.KafkaMessagesBundle

object AwsSettingsConst {
    val S3_ACCESS_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.access.key"))
    val S3_SECRET_KEY: ModificationKey = ModificationKey(KafkaMessagesBundle.message("settings.s3.secret.key"))
}