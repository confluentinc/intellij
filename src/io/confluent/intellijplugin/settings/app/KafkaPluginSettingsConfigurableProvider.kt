package io.confluent.intellijplugin.settings.app

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import io.confluent.intellijplugin.core.constants.BdtPlugins

class KafkaPluginSettingsConfigurableProvider() : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = KafkaPluginSettingsConfigurable()

    override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}