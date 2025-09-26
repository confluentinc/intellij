package io.confluent.intellijplugin.core.rfs.settings.local

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys
import io.confluent.intellijplugin.core.settings.CommonSettingsKeys.ROOT_PATH_KEY
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.defaultui.SettingsPanelCustomizer
import io.confluent.intellijplugin.core.settings.fields.BrowseTextField
import io.confluent.intellijplugin.core.settings.fields.StringNamedField
import io.confluent.intellijplugin.core.settings.fields.WrappedComponent
import io.confluent.intellijplugin.core.settings.withEmptyOrFileExistValidator
import io.confluent.intellijplugin.core.settings.withNotEmptyValidator
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class RfsLocalSettingsCustomizer(connectionData: RfsLocalConnectionData, uiDisposable: Disposable)
  : SettingsPanelCustomizer<RfsLocalConnectionData>()
{
  private val nameField = StringNamedField(ConnectionData::name, CommonSettingsKeys.NAME_KEY, connectionData)
    .withNotEmptyValidator(uiDisposable, KafkaMessagesBundle.message("validator.nameField"))

  private val pathField = BrowseTextField(
    prop = RfsLocalConnectionData::rootPath,
    ROOT_PATH_KEY,
    FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(KafkaMessagesBundle.message("rfs.local.driver.root")),
    initSettings = connectionData
  ).apply {
    withEmptyOrFileExistValidator(uiDisposable, canBeEmpty = false)
  }

  override fun getDefaultFields() = listOf<WrappedComponent<in RfsLocalConnectionData>>(nameField, pathField)
}
