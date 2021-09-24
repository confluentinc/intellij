package com.jetbrains.bigdatatools.kafka.producer.models

import java.util.*

data class ConsumerResultMessage(val key: String,
                                 val value: String,
                                 val timestamp: Date,
                                 val offset: Long,
                                 val partition: Int,
                                 val duration: Int)