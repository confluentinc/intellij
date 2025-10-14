package io.confluent.intellijplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import io.confluent.intellijplugin.core.constants.BdtPlugins

class TelemetryConfigurableProvider : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = TelemetryConfigurable()

    override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}

