package io.confluent.intellijplugin.rfs

import io.confluent.intellijplugin.core.settings.components.RenderableEntity
import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class KafkaPropertiesSource(override val title: String) : RenderableEntity {
    FIELD(KafkaMessagesBundle.message("kafka.property.source.field")),
    FILE(KafkaMessagesBundle.message("kafka.property.source.file"));

    override val id = name
}