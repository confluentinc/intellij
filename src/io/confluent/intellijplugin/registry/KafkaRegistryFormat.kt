package io.confluent.intellijplugin.registry

import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class KafkaRegistryFormat(@Nls val presentable: String) {
    AVRO(KafkaMessagesBundle.message("registry.format.avro")),
    PROTOBUF(KafkaMessagesBundle.message("registry.format.protobuf")),
    JSON(KafkaMessagesBundle.message("registry.format.json")),
    UNKNOWN(KafkaMessagesBundle.message("registry.format.unknown"));

    companion object {
        fun parse(s: String?) = entries.firstOrNull { it.name.lowercase() == s?.lowercase() } ?: UNKNOWN

        /**
         * Map Schema Registry schema type string to format enum.
         * Per SR convention, null schemaType defaults to AVRO.
         */
        fun fromSchemaType(type: String?): KafkaRegistryFormat = when (type?.uppercase()) {
            "PROTOBUF" -> PROTOBUF
            "JSON" -> JSON
            else -> AVRO  // null or "AVRO"
        }
    }
}