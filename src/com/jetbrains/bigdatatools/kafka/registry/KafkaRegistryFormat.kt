package com.jetbrains.bigdatatools.kafka.registry

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryFormat(@Nls val presentable: String) {
  AVRO(KafkaMessagesBundle.message("registry.format.avro")),
  PROTOBUF(KafkaMessagesBundle.message("registry.format.protobuf")),
  JSON(KafkaMessagesBundle.message("registry.format.json")),
  UNKNOWN(KafkaMessagesBundle.message("registry.format.deleted"));

  override fun toString(): String = presentable

  companion object {
    fun parse(s: String?) = values().firstOrNull { it.name.lowercase() == s?.lowercase() } ?: UNKNOWN
  }
}