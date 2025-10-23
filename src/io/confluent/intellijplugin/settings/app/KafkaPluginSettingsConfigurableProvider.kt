package io.confluent.intellijplugin.settings.app

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import io.confluent.intellijplugin.core.constants.BdtPlugins

/**
 * Provider that creates the Kafka plugin settings configurable if the Kafka plugin is installed.
 * @see KafkaPluginSettingsConfigurable
 */
class KafkaPluginSettingsConfigurableProvider() : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = KafkaPluginSettingsConfigurable()

    override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}
