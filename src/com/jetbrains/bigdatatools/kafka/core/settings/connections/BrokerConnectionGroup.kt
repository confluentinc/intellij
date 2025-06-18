package com.jetbrains.bigdatatools.kafka.core.settings.connections

import com.intellij.icons.AllIcons
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class BrokerConnectionGroup : ConnectionGroup(
  id = GROUP_ID,
  name = KafkaMessagesBundle.message("connection.group.name.broker"),
  icon = AllIcons.Toolwindows.ToolWindowMessages
) {
  companion object {
    const val GROUP_ID: String = "BrokerConnectionGroup"
  }
}