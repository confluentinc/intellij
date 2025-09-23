package io.confluent.kafka.consumer.models

data class ConsumerGroup(val groupId: String, val isEnabledAutoCommit: Boolean)