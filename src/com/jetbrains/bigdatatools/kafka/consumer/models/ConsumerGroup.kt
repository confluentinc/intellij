package com.jetbrains.bigdatatools.kafka.consumer.models

data class ConsumerGroup(val groupId: String, val isEnabledAutoCommit: Boolean)