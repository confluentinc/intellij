package io.confluent.intellijplugin.consumer.models

import io.confluent.intellijplugin.util.KafkaMessagesBundle

enum class ConsumerLimitType(val title: String) {
    NONE(KafkaMessagesBundle.message("consumer.limit.type.none")),
    TOPIC_NUMBER_RECORDS(KafkaMessagesBundle.message("consumer.limit.type.topicRecords")),
    DATE(KafkaMessagesBundle.message("consumer.limit.type.date")),
    TOPIC_MAX_SIZE(KafkaMessagesBundle.message("consumer.limit.type.topicMaxSize")),
    PARTITION_NUMBER_RECORDS(KafkaMessagesBundle.message("consumer.limit.type.partitionRecords")),
    PARTITION_MAX_SIZE(KafkaMessagesBundle.message("consumer.limit.type.partitionMaxSize"))
}