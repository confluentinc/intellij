package com.jetbrains.bigdatatools.kafka.core.rfs.settings.local

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.jetbrains.bigdatatools.kafka.core.settings.CommonSettingsKeys
import com.jetbrains.bigdatatools.kafka.core.settings.CommonSettingsKeys.ROOT_PATH_KEY
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.SettingsPanelCustomizer
import com.jetbrains.bigdatatools.kafka.core.settings.fields.BrowseTextField
import com.jetbrains.bigdatatools.kafka.core.settings.fields.StringNamedField
import com.jetbrains.bigdatatools.kafka.core.settings.fields.WrappedComponent
import com.jetbrains.bigdatatools.kafka.core.settings.withEmptyOrFileExistValidator
import com.jetbrains.bigdatatools.kafka.core.settings.withNotEmptyValidator
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

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
