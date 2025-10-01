package io.confluent.intellijplugin.core.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.constants.BdtPlugins

class KafkaSettingsConfigurableProvider(val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = ConnectionsConfigurable(project)

  override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}