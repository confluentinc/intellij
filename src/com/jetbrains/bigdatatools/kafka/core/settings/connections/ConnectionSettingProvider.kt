package com.jetbrains.bigdatatools.kafka.core.settings.connections

import com.intellij.openapi.extensions.ExtensionPointName
import com.jetbrains.bigdatatools.kafka.core.constants.BdtPluginType
import com.jetbrains.bigdatatools.kafka.core.constants.BdtPlugins
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.kafka.core.util.InternalFeature

/**
 * User: Dmitry.Naydanov
 * Date: 2019-04-17.
 */
interface ConnectionSettingProvider {
  fun createConnectionGroups(): List<ConnectionGroup>
  fun retrieveSearchKeywords(): List<Pair<String, String>> = createConnectionGroups().map { Pair(it.name.lowercase(), it.name) }
  val pluginType: BdtPluginType
}

interface InternalConnectionSettingsProvider : ConnectionSettingProvider, InternalFeature

object ConnectionSettingProviderEP {
  private val EP_NAME = ExtensionPointName.create<ConnectionSettingProvider>("com.intellij.bigdatatools.kafka.connectionSettingProvider")
  fun getAll(): List<ConnectionSettingProvider> {
    val providerList = if (BdIdeRegistryUtil.isInternalFeaturesAvailable())
      EP_NAME.extensionList
    else
      EP_NAME.extensionList.filter { it !is InternalConnectionSettingsProvider }
    return providerList.filter { BdtPlugins.isPluginInstalled(it.pluginType) }
  }

  fun getGroups() = getAll().flatMap { it.createConnectionGroups() }.filter {
    it.parentGroupId == null || BdtPlugins.isSupportedConnectionGroup(it.id)
  }

  fun getConnectionFactories(): List<ConnectionFactory<*>> = getGroups().filterIsInstance<ConnectionFactory<*>>()
}