package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.bigdatatools.common.connection.tunnel.ui.SshTunnelComponent
import com.jetbrains.bigdatatools.common.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.StringNamedField
import com.jetbrains.bigdatatools.common.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.block
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {
  override val tunnelField: SshTunnelComponent<KafkaConnectionData> = SshTunnelComponent(project, uiDisposable, connectionData,
                                                                                         hostAndPortProvider)

  override val url = StringNamedField(ConnectionData::uri, ModificationKey(KafkaMessagesBundle.message("settings.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.url.text.empty")
      getTextComponent().toolTipText = KafkaMessagesBundle.message("settings.url.text.hint")
    }.withValidator(uiDisposable, ::validateBrokerNames) as StringNamedField

  private val brokerSettings = KafkaBrokerSettings(project, connectionData, uiDisposable, url)
  private val registrySettings = KafkaRegistrySettings(project, connectionData, uiDisposable)

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> {
    return listOf<WrappedComponent<in KafkaConnectionData>>(nameField, url,
                                                            tunnelField) + brokerSettings.getDefaultFields() + registrySettings.getDefaultFields()
  }

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = panel {
    row(nameField).topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    brokerSettings.setPanelComponent(this)
    registrySettings.setPanelComponent(this)

    block(tunnelField.getComponent()).topGap(TopGap.SMALL)
  }

  private fun validateBrokerNames(names: String): String? {
    if (names.isBlank())
      return KafkaMessagesBundle.message("settings.url.must.be.non.empty.hint")
    val brokers = names.split(",").map { it.trim() }
    val errors = brokers.map { it to BdtUrlUtils.validateUrl(it) }.filter { it.second != null }
    return errors.firstOrNull()?.let { "${it.first}: ${it.second ?: MessagesBundle.message("unexpected.error")}" }
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val REGISTRY_PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val CONFIGURATION_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("kafka.property.source.label"))
    val REGISTRY_PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}