package io.confluent.kafka.core.monitoring

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.connection.tunnel.model.TunnelableData
import io.confluent.kafka.core.connection.tunnel.ui.SshTunnelComponent
import io.confluent.kafka.core.settings.CommonSettingsKeys
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.core.settings.defaultui.HostAndPortChangeListener
import io.confluent.kafka.core.settings.defaultui.HostAndPortProvider
import io.confluent.kafka.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.kafka.core.settings.defaultui.registerOnTextComponent
import io.confluent.kafka.core.settings.fields.StringNamedField
import io.confluent.kafka.core.settings.withNotEmptyValidator
import io.confluent.kafka.core.settings.withUrlValidator
import io.confluent.kafka.util.KafkaMessagesBundle

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