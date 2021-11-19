package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.settings.ModificationKey
import com.jetbrains.bigdatatools.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.settings.fields.*
import com.jetbrains.bigdatatools.settings.withNotEmptyValidator
import com.jetbrains.bigdatatools.ui.MigPanel

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {

  private val propertiesEditor = PropertiesFieldComponent.create(KafkaConnectionData::properties,
    KafkaSettingsKeys.PROPERTIES_KEY,
    connectionData, uiDisposable)

  private val propertiesFile: WrappedTextComponent<KafkaConnectionData, *> = BrowseTextField(KafkaConnectionData::propertyFilePath,
    KafkaSettingsKeys.PROPERTIES_FILE_KEY, connectionData,
    browseTitle = KafkaMessagesBundle.message("settings.properties.file.browse"),
    fileChooserDescriptor = com.intellij.openapi.fileChooser.FileChooserDescriptorFactory.createSingleFileDescriptor())
    .withNotEmptyValidator(uiDisposable)


  private val sourceTypeChooser = ComboBoxField(KafkaConnectionData::propertySource,
    KafkaSettingsKeys.PROPERTIES_SOURCE_KEY,
    connectionData,
    arrayOf(KafkaPropertySource.DIRECT, KafkaPropertySource.FILE)) {
    when (it) {
      KafkaPropertySource.DIRECT -> KafkaPropertySource.DIRECT.title
      KafkaPropertySource.FILE -> KafkaPropertySource.FILE.title
    }
  }.apply {
    getComponent().setMinLength(Int.MAX_VALUE)
  }

  init {
    sourceTypeChooser.getComponent().addItemListener {
      updateAuthStatus()
    }
    updateAuthStatus()
  }

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, propertiesEditor, propertiesFile, tunnelField, sourceTypeChooser)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = MigPanel().apply {
    row(nameField)
    row(url)
    row(propertiesEditor)

    row(KafkaMessagesBundle.message("settings.property.source"), sourceTypeChooser.getComponent())
    row(propertiesEditor)
    row(propertiesFile)

    add(tunnelField.getComponent(), UiUtil.spanXWrap)
  }

  private fun updateAuthStatus() {
    val authType = sourceTypeChooser.getValue()

    propertiesEditor.isVisible = authType == KafkaPropertySource.DIRECT
    propertiesFile.isVisible = authType == KafkaPropertySource.FILE
  }


  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}