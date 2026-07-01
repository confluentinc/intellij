package io.confluent.intellijplugin.core.monitoring

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import io.confluent.intellijplugin.core.connection.tunnel.model.TunnelableData
import io.confluent.intellijplugin.core.connection.tunnel.ui.SshModuleAvailability
import io.confluent.intellijplugin.core.connection.tunnel.ui.SshTunnelComponent
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.defaultui.HostAndPortChangeListener
import io.confluent.intellijplugin.core.settings.defaultui.HostAndPortProvider
import io.confluent.intellijplugin.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.intellijplugin.core.settings.defaultui.registerOnTextComponent
import io.confluent.intellijplugin.core.settings.fields.StringNamedField
import io.confluent.intellijplugin.core.settings.withNotEmptyValidator
import io.confluent.intellijplugin.core.settings.withUrlValidator
import io.confluent.intellijplugin.util.KafkaMessagesBundle

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
        override fun registerChangeListener(listener: HostAndPortChangeListener) =
            registerOnTextComponent(url.getTextComponent(), listener)
    }

    /**
     * SSH tunnel UI, or `null` when the Remote/SSH module is absent (e.g. WebStorm) — constructing
     * [SshTunnelComponent] hard-references `com.intellij.ssh.*`. See [SshModuleAvailability].
     */
    open val tunnelField: SshTunnelComponent<D>? =
        if (SshModuleAvailability.isAvailable)
            SshTunnelComponent(project, uiDisposable, connectionData, hostAndPortProvider)
        else
            null
}