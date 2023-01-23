package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.components.ActionLink
import com.jetbrains.bigdatatools.common.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.settings.withNotEmptyValidator
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.MigPanel
import com.jetbrains.bigdatatools.common.ui.SimpleDumbAwareAction
import com.jetbrains.bigdatatools.common.ui.doOnChange
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.KafkaPropertySource
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {

  override val url = StringNamedField(ConnectionData::uri, ModificationKey(KafkaMessagesBundle.message("settings.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.url.text.empty")
      getTextComponent().toolTipText = KafkaMessagesBundle.message("settings.url.text.hint")
    }
    .withValidator(uiDisposable, ::validateBucketsNames) as StringNamedField

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

  private val generateAction = ActionLink(MessagesBundle.message("settings.connection.properties.add.config")) {
    generateAction()
  }

  private val sourceTypeChooser = RadioGroupField(KafkaConnectionData::propertySource,
                                                  KafkaSettingsKeys.PROPERTIES_SOURCE_KEY,
                                                  connectionData,
                                                  KafkaPropertySource.values())

  private val registryUrl = StringNonRequiredField(
    KafkaConnectionData::registryUrl,
    ModificationKey(KafkaMessagesBundle.message("settings.registry.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.registry.url.hint")
    }
    .withValidator(uiDisposable) {
      if (it.isBlank())
        return@withValidator null
      val isValid = BdtUrlUtils.isValidUrl(it)
      if (!isValid) MessagesBundle.message("url.format.error") else null
    }

  private val registryProperties = PropertiesFieldComponent.create(
    project,
    KafkaPropertiesUtils.getAdminPropertiesDescriptions(),
    KafkaConnectionData::registryProperties,
    ModificationKey(KafkaMessagesBundle.message("settings.registry.additional.properties")),
    connectionData, uiDisposable)


  init {
    sourceTypeChooser.addItemListener {
      updateAuthStatus()
    }
    updateAuthStatus()
  }

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, propertiesEditor, propertiesFile, tunnelField, sourceTypeChooser, registryUrl, registryProperties)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = MigPanel().apply {
    row(nameField)
    row(url)

    shortRow(sourceTypeChooser)
    gapLeft = true
    row(generateAction)
    row(propertiesEditor)
    row(propertiesFile)
    gapLeft = false

    separatorRow()

    title(KafkaMessagesBundle.message("settings.registry.title"))
    row(registryUrl)
    row(registryProperties)

    separatorRow()
    block(tunnelField.getComponent())


    propertiesEditor.getComponent().document.doOnChange {
      this@apply.revalidate()
    }
    registryProperties.getComponent().document.doOnChange {
      this@apply.revalidate()
    }
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

  private fun generateAction() {
    val actionGroup = DefaultActionGroup()

    actionGroup.add(SimpleDumbAwareAction(KafkaMessagesBundle.message("settings.generate.ssl")) {
      propertiesEditor.addConfig(mapOf(
        "security.protocol" to "SSL",
        "ssl.truststore.location" to "<TRUSTORE_PATH>",
        "ssl.truststore.password" to "<TRUSTORE_PASSWORD>",
        "ssl.keystore.type" to "PKCS12",
        "ssl.keystore.location" to "<KEYSTORE_PATH>",
        "ssl.keystore.password" to "<KEYSTORE_PASSWORD>",
        "ssl.keystore.password" to "<KEYSTORE_PASSWORD>",
        "ssl.endpoint.identification.algorithm" to "<KEY_PASSWORD>"
      ))
    })


    actionGroup.add(SimpleDumbAwareAction(KafkaMessagesBundle.message("settings.generate.kerberos.with.jaas")) {
      propertiesEditor.addConfig(mapOf(
        "security.protocol" to "SASL_PLAINTEXT",
        "sasl.kerberos.service.name" to "kafka",
        "sasl.jaas.config" to "com.sun.security.auth.module.Krb5LoginModule required " +
          "useKeyTab=true " +
          "keyTab=\"PATH_TO_KEYTAB\" " +
          "storeKey=true " +
          "useTicketCache=false " +
          "serviceName=\"kafka\" " +
          "principal=\"USER/HOST@REALM\";"
      ))
    })


    val popupMenu = JBPopupFactory.getInstance().createActionGroupPopup(null, actionGroup,
                                                                        DataManager.getInstance().getDataContext(generateAction),
                                                                        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
    popupMenu.showUnderneathOf(generateAction)
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}