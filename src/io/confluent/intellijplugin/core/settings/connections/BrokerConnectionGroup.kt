package io.confluent.intellijplugin.core.settings.connections

import com.intellij.icons.AllIcons
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class BrokerConnectionGroup : ConnectionGroup(
    id = GROUP_ID,
    name = KafkaMessagesBundle.message("connection.group.name.broker"),
    icon = AllIcons.Toolwindows.ToolWindowMessages
) {
    companion object {
        const val GROUP_ID: String = "BrokerConnectionGroup"
    }
}