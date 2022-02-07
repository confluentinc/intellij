package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import com.jetbrains.bigdatatools.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.settings.CommonSettingsKeys
import com.jetbrains.bigdatatools.settings.ModificationKey
import com.jetbrains.bigdatatools.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.settings.fields.*
import com.jetbrains.bigdatatools.settings.withNotEmptyValidator
import com.jetbrains.bigdatatools.settings.withValidator
import com.jetbrains.bigdatatools.ui.MigPanel
import com.jetbrains.bigdatatools.util.BdtUrlUtils
import com.jetbrains.bigdatatools.util.MessagesBundle

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {

  override val url = StringNamedField(ConnectionData::uri, CommonSettingsKeys.URL_KEY, connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.url.text.empty")
      getTextComponent().toolTipText = KafkaMessagesBundle.message("settings.url.text.hint")
    }
    .withValidator(uiDisposable, ::validateBucketsNames)

  private val propertiesEditor = PropertiesFieldComponent.create(
    project,
    KafkaPropertiesUtils.getAdminPropertiesDescriptions(),
    KafkaConnectionData::properties,
    KafkaSettingsKeys.PROPERTIES_KEY,
    connectionData, uiDisposable)

  private val propertiesFile = BrowseTextField(KafkaConnectionData::propertyFilePath,
                                               KafkaSettingsKeys.PROPERTIES_FILE_KEY,
                                               connectionData,
                                               browseTitle = KafkaMessagesBundle.message(
                                                 "settings.properties.file.browse"),
                                               fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor())
    .withNotEmptyValidator(uiDisposable)

  private val sourceTypeChooser = RadioGroupField(KafkaConnectionData::propertySource,
                                                  KafkaSettingsKeys.PROPERTIES_SOURCE_KEY,
                                                  connectionData,
                                                  KafkaPropertySource.values())

  init {
    sourceTypeChooser.addItemListener {
      updateAuthStatus()
    }
    updateAuthStatus()
  }

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, propertiesEditor, propertiesFile, tunnelField, sourceTypeChooser)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = MigPanel().apply {
    row(nameField)
    row(url)

    shortRow(sourceTypeChooser)
    gapLeft = true
    row(propertiesEditor)
    row(propertiesFile)
    gapLeft = false

    separatorRow()
    block(tunnelField.getComponent())
  }

  private fun updateAuthStatus() {
    val authType = sourceTypeChooser.getValue()
    propertiesEditor.isVisible = authType == KafkaPropertySource.DIRECT
    propertiesFile.isVisible = authType == KafkaPropertySource.FILE
  }

  private fun validateBucketsNames(names: String): String? {
    if (names.isBlank())
      return KafkaMessagesBundle.message("settings.url.must.be.non.empty.hint")
    val brokers = names.split(",").map { it.trim() }
    val errors = brokers.map { it to BdtUrlUtils.validateUrl(it) }.filter { it.second != null }
    return errors.firstOrNull()?.let { "${it.first}: ${it.second ?: MessagesBundle.message("unexpected.error")}" }
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}