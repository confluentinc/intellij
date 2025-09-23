package io.confluent.kafka.common.models

import io.confluent.kafka.common.settings.StorageConfig

/** Marker interface for RunConsumerConfig and  RunProducerConfig.*/
interface RunConfig {
  fun toStorage(): StorageConfig
}