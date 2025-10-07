package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class KafkaCustomSchemaSource(val title: String) {
    FILE(KafkaMessagesBundle.message("custom.shema.source.type.file")),
    IMPLICIT(KafkaMessagesBundle.message("custom.shema.source.type.implicit"))
}