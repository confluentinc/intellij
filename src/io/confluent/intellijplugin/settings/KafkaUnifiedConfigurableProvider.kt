package io.confluent.intellijplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurableProvider
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.constants.BdtPlugins

class KafkaUnifiedConfigurableProvider(private val project: Project) : ConfigurableProvider() {
    override fun createConfigurable(): Configurable = KafkaUnifiedConfigurable(project)

    override fun canCreateConfigurable(): Boolean = BdtPlugins.isKafkaPluginInstalled()
}

