package com.jetbrains.bigdatatools.kafka.ui

import java.util.*

data class ProducerResultMessage(val text: String, val timestamp: Date, val offset: Long, val partition: Int, val duration: Int)