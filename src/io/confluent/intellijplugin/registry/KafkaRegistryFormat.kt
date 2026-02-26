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
        fun parse(s: String?): KafkaRegistryFormat {
            val normalized = s?.trim()
            if (normalized.isNullOrEmpty()) {
                return AVRO
            }
            return entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) } ?: UNKNOWN
        fun parse(s: String?) = entries.firstOrNull { it.name.lowercase() == s?.lowercase() } ?: UNKNOWN

        /**
         * Map Schema Registry schema type string to format enum.
         * Per SR convention, null schemaType defaults to AVRO.
         * Unrecognized types return UNKNOWN.
         */
        fun fromSchemaType(type: String?): KafkaRegistryFormat = when (type?.uppercase()) {
            null, "AVRO" -> AVRO  // null defaults to AVRO per SR convention
            "PROTOBUF" -> PROTOBUF
            "JSON" -> JSON
            else -> UNKNOWN
        }
    }
}
