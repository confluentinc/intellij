package com.jetbrains.bigdatatools.kafka.common.models

import com.jetbrains.bigdatatools.kafka.common.settings.StorageConfig

/** Marker interface for RunConsumerConfig and  RunProducerConfig.*/
interface RunConfig {
  fun toStorage(): StorageConfig
}