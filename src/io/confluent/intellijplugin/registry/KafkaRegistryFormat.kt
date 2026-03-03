package io.confluent.intellijplugin.registry

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryFormat(@Nls val presentable: String) {
    AVRO(KafkaMessagesBundle.message("registry.format.avro")),
    PROTOBUF(KafkaMessagesBundle.message("registry.format.protobuf")),
    JSON(KafkaMessagesBundle.message("registry.format.json")),
    UNKNOWN(KafkaMessagesBundle.message("registry.format.unknown"));

    companion object {
        // Per Schema Registry spec, AVRO is the default when schemaType is not specified
        fun parse(s: String?) = entries.firstOrNull { it.name.lowercase() == s?.lowercase() } ?: AVRO
    }
}