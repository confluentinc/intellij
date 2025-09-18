package com.jetbrains.bigdatatools.kafka.core.monitoring

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model.TunnelableData
import com.jetbrains.bigdatatools.kafka.core.connection.tunnel.ui.SshTunnelComponent
import com.jetbrains.bigdatatools.kafka.core.settings.CommonSettingsKeys
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.HostAndPortChangeListener
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.HostAndPortProvider
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.SettingsPanelCustomizer
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.registerOnTextComponent
import com.jetbrains.bigdatatools.kafka.core.settings.fields.StringNamedField
import com.jetbrains.bigdatatools.kafka.core.settings.withNotEmptyValidator
import com.jetbrains.bigdatatools.kafka.core.settings.withUrlValidator
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

abstract class TunnelableSettingsCustomizer<D>(
  connectionData: D,
  protected val project: Project,
  uiDisposable: Disposable
) : SettingsPanelCustomizer<D>() where D : ConnectionData, D : TunnelableData {
  open val nameField = StringNamedField(ConnectionData::name, CommonSettingsKeys.NAME_KEY, connectionData)
    .withNotEmptyValidator(uiDisposable, KafkaMessagesBundle.message("validator.nameField")) as StringNamedField

  open val url = StringNamedField(ConnectionData::uri, CommonSettingsKeys.URL_KEY, connectionData)
    .withUrlValidator(uiDisposable)

  protected val hostAndPortProvider = object : HostAndPortProvider {
    override fun registerChangeListener(listener: HostAndPortChangeListener) = registerOnTextComponent(url.getTextComponent(), listener)
  }

  open val tunnelField: SshTunnelComponent<D> = SshTunnelComponent(project, uiDisposable, connectionData, hostAndPortProvider)
  val enableTunnelField by lazy { tunnelField.isEnabledCheckBox }
}