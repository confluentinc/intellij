package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class SchemaRegistryAuthType(override val title: String) : RenderableEntity {
    NOT_SPECIFIED(KafkaMessagesBundle.message("kafka.auth.none")),
    BASIC_AUTH(KafkaMessagesBundle.message("kafka.auth.basic")),
    BEARER(KafkaMessagesBundle.message("kafka.auth.bearer"));

    override val id = name
}
