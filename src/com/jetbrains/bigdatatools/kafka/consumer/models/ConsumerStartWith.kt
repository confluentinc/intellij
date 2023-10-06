package com.jetbrains.bigdatatools.kafka.consumer.models

import org.jetbrains.annotations.Nls

/**
 * Depending of type
 */
data class ConsumerStartWith(val type: ConsumerStartType, val time: Long?, val offset: Long?, @Nls val consumerGroup: String?)