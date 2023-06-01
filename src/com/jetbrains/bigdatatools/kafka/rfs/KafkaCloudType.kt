package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.util.NlsContexts
import com.jetbrains.bigdatatools.core.settings.components.RenderableEntity
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

// The name does not reflect the meaning. This is only the connection settings type "user defined in UI" vs "taken from properties file".
enum class KafkaCloudType(@NlsContexts.RadioButton override val title: String) : RenderableEntity {
  CONFLUENT(KafkaMessagesBundle.message("settings.cloud.type.confluent")),
  AWS_MSK(KafkaMessagesBundle.message("settings.cloud.type.msk"));

  override val id = name.lowercase()
}