package io.confluent.intellijplugin.core.settings.connections

import io.confluent.intellijplugin.core.constants.BdtPluginType

class GeneralConnectionSettingsProvider : ConnectionSettingProvider {
    override val pluginType: BdtPluginType = BdtPluginType.KAFKA

    override fun createConnectionGroups(): List<ConnectionGroup> = listOf(
        BrokerConnectionGroup(),
        CCloudDisplayGroup()
    )
}