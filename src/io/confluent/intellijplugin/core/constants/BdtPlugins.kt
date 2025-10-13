package io.confluent.intellijplugin.core.constants

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.NlsContexts
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.util.KafkaMessagesBundle

object BdtPlugins {
    const val KAFKA_ID = "com.intellij.bigdatatools.kafka"
    const val RFS_ID = "com.intellij.bigdatatools.rfs"
    const val FULL_ID = "com.intellij.bigdatatools"

    val ALL_PLUGINS = BdtPluginType.entries.map { it.pluginId }.toSet()

    fun isFullPluginInstalled() = isPluginInstalled(BdtPluginType.FULL)
    fun isKafkaPluginInstalled() = isPluginInstalled(BdtPluginType.KAFKA)
    fun isRfsPluginInstalled() = isPluginInstalled(BdtPluginType.RFS)

    fun isPluginInstalled(pluginType: BdtPluginType) = isPluginExistsAndEnabled(pluginType.pluginId)

    private fun isPluginExistsAndEnabled(id: String): Boolean {
        val pluginId = PluginId.findId(id) ?: return false
        val pluginDescriptor = PluginManagerCore.getPlugin(pluginId) ?: return false
        return pluginDescriptor.isEnabled
    }

    fun isSupportedConnectionGroup(groupId: String): Boolean {
        val connType = BdtConnectionType.getForId(groupId) ?: return false
        return isPluginInstalled(connType.pluginType)
    }

    fun ConnectionData.isSupportedByPlugin() = isSupportedConnectionGroup(groupId)

    @NlsContexts.TabTitle
    fun calculateTitle(): String {
        val isKafka = isKafkaPluginInstalled()
        val isRfs = isRfsPluginInstalled()

        return when {
            isRfs && !isKafka -> KafkaMessagesBundle.message("tool.window.title.rfs")
            isRfs && isKafka -> KafkaMessagesBundle.message("tool.window.title.rfs.kafka")
            else -> KafkaMessagesBundle.message("tool.window.title")
        }
    }
}