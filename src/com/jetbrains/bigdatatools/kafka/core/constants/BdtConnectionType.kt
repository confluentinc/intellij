package com.jetbrains.bigdatatools.kafka.core.constants

import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

enum class BdtConnectionType(val id: String, @Nls val connName: String, val pluginType: BdtPluginType) {
  LOCAL("RfsLocalConnectionGroup", KafkaMessagesBundle.message("rfs.local.connection.name"), BdtPluginType.RFS),
  KAFKA("KafkaConnections", KafkaMessagesBundle.message("connection.kafka.default.name"), BdtPluginType.KAFKA),
  TEST("TestConnections", KafkaMessagesBundle.message("connection.test.default.name"), BdtPluginType.FULL);

  companion object {
    fun getForId(id: String): BdtConnectionType? = entries.firstOrNull { it.id == id }
  }
}