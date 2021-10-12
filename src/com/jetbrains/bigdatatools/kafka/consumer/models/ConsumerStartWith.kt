package com.jetbrains.bigdatatools.kafka.consumer.models

/**
 * Depending of type
 */
data class ConsumerStartWith(val type: ConsumerStartType, val time: Long?, val offset: Long?, val consumerGroup: String?)