package io.confluent.kafka.core.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.constants.BdtPlugins

class KafkaSettingsConfigurableProvider(val project: Project) : ConfigurableProvider() {
  override fun createConfigurable(): Configurable = ConnectionsConfigurable(project)

  override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}