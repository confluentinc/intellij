package com.jetbrains.bigdatatools.kafka.core.constants

enum class BdtPluginType(val pluginId: String) {
  RFS(BdtPlugins.RFS_ID),
  KAFKA(BdtPlugins.KAFKA_ID),
  FULL(BdtPlugins.FULL_ID)
}