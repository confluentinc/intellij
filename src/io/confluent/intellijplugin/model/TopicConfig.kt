package io.confluent.intellijplugin.model

import io.confluent.intellijplugin.core.monitoring.data.model.RemoteInfo
import io.confluent.intellijplugin.util.KafkaLocalizedField

data class TopicConfig(
  val name: String,
  val value: String,
  val defaultValue: String = "") : RemoteInfo {
  companion object {
    val renderableColumns: List<KafkaLocalizedField<TopicConfig>> by lazy {
      listOf(
        KafkaLocalizedField(TopicConfig::name, "data.TopicConfig.name"),
        KafkaLocalizedField(TopicConfig::value, "data.TopicConfig.value"),
        KafkaLocalizedField(TopicConfig::defaultValue, "data.TopicConfig.defaultValue")
      )
    }
  }
}