package io.confluent.intellijplugin.model

data class ConsumerPosition(val seekType: SeekType, val seekTo: Map<Int, Long>)