package io.confluent.intellijplugin.consumer.models

import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class ConsumerFilterType(val title: String) {
    NONE(KafkaMessagesBundle.message("consumer.filter.type.none")),
    CONTAINS(KafkaMessagesBundle.message("consumer.filter.type.contains")),
    DOES_NOT_CONTAINS(KafkaMessagesBundle.message("consumer.filter.type.notContains")),
    REGEX(KafkaMessagesBundle.message("consumer.filter.type.regexp"))
}