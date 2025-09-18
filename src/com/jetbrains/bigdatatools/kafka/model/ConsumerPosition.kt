package com.jetbrains.bigdatatools.kafka.model

data class ConsumerPosition(val seekType: SeekType, val seekTo: Map<Int, Long>)