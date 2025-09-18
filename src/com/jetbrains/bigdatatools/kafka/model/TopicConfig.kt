package com.jetbrains.bigdatatools.kafka.model

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.model.RemoteInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaLocalizedField

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