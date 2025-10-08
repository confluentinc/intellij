package io.confluent.intellijplugin.common.models

import io.confluent.intellijplugin.common.settings.StorageConfig

/** Marker interface for RunConsumerConfig and  RunProducerConfig.*/
interface RunConfig {
    fun toStorage(): StorageConfig
}