package io.confluent.intellijplugin.toolwindow.controllers

import com.intellij.openapi.util.NlsSafe
import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class KafkaGroupType(@NlsSafe val title: String) {
    TOPIC(KafkaMessagesBundle.message("settings.topics.tab")),
    CONSUMER_GROUP(KafkaMessagesBundle.message("settings.consumers.tab")),
    CONSUMER_GROUP_OFFSET(KafkaMessagesBundle.message("settings.consumer.offset.tab")),
    SCHEMA_REGISTRY_GROUP(KafkaMessagesBundle.message("settings.registry.tab")),
    TOPIC_DETAIL("Topic details"),
    SCHEMA_DETAIL("Schema details"),
}