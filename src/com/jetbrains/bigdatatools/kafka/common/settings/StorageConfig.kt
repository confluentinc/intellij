package com.jetbrains.bigdatatools.kafka.common.settings

import com.jetbrains.bigdatatools.kafka.common.models.RunConfig

interface StorageConfig {
  fun fromStorage(): RunConfig
}