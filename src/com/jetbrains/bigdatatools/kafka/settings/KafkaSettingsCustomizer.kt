package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.rfs.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import org.apache.kafka.common.security.auth.SecurityProtocol

class KafkaSettingsCustomizer(project: Project, connectionData: KafkaConnectionData, uiDisposable: Disposable) :
  TunnableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {

  override val url = StringNamedField(ConnectionData::uri, ModificationKey(KafkaMessagesBundle.message("settings.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.url.text.empty")
      getTextComponent().toolTipText = KafkaMessagesBundle.message("settings.url.text.hint")
    }
    .withValidator(uiDisposable, ::validateBrokerNames) as StringNamedField

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

  private lateinit var implicitClientSettingsGroup: RowsRange
  private lateinit var authType: Cell<ComboBox<KafkaSecurityType>>
  private lateinit var saslGroup: RowsRange
  private lateinit var sslGroup: RowsRange
  private lateinit var saslCredentialsGroup: RowsRange
  private lateinit var saslKerberosGroup: RowsRange
  private lateinit var saslSecurityProtocol: Cell<ComboBox<SecurityProtocol>>
  private lateinit var saslMechanism: Cell<ComboBox<KafkaSaslMechanism>>
  private lateinit var sslUseKeystore: Cell<JBCheckBox>
  private lateinit var sslKeystoreGroup: RowsRange

  private lateinit var schemaAuth: Cell<ComboBox<SchemaRegistryAuthType>>
  private lateinit var schemaBasicAuthGroup: RowsRange
  private lateinit var schemaBasicLogin: Row
  private lateinit var schemaBasicPassword: Row
  private lateinit var schemaBearerToken: Row

  init {
    sourceTypeChooser.addItemListener {
      updateAuthStatus()
    }
  }

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(nameField, url, propertiesEditor, propertiesFile, tunnelField, sourceTypeChooser, registryUrl, registryProperties)

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = panel {
    row(nameField)
    row(url)

    row(sourceTypeChooser)
    row(propertiesFile)


    implicitClientSettingsGroup = rowsRange {
      row(KafkaMessagesBundle.message("kafka.auth.method.label")) {
        authType = comboBox(KafkaSecurityType.values().toList(), CustomListCellRenderer<KafkaSecurityType> { it.title }).onChanged {
          updateVisibilityOfAuth()
        }
      }

      saslGroup = rowsRange {
        row(KafkaMessagesBundle.message("kafka.security.protocol.label")) {
          saslSecurityProtocol = comboBox(listOf(SecurityProtocol.SASL_PLAINTEXT, SecurityProtocol.SASL_SSL),
                                          CustomListCellRenderer<SecurityProtocol> { it.name })
        }

        row(KafkaMessagesBundle.message("kafka.sasl.mechanism")) {
          saslMechanism = comboBox(KafkaSaslMechanism.values().toList(),
                                   CustomListCellRenderer<KafkaSaslMechanism> { it.title }).onChanged {
            updateVisibilityOfSasl()
          }
        }


        saslKerberosGroup = rowsRange {
          row(MessagesBundle.message("kerberos.settings.principal.label")) {
            textField()
          }
          row(MessagesBundle.message("kerberos.connection.settings.keytab.label")) {
            textFieldWithBrowseButton(project = project,
                                      browseDialogTitle = MessagesBundle.message("kerberos.connection.settings.keytab.select.dialog.title"))
          }
        }


        saslCredentialsGroup = rowsRange {
          row(KafkaMessagesBundle.message("kafka.username")) {
            textField()
          }
          row(KafkaMessagesBundle.message("kafka.password")) {
            passwordField()
          }
        }
      }


      sslGroup = rowsRange {
        row(KafkaMessagesBundle.message("kafka.truststore.location")) {
          textFieldWithBrowseButton(project = project,
                                    browseDialogTitle = KafkaMessagesBundle.message("kafka.truststore.location.dialog.title"))
        }
        row(KafkaMessagesBundle.message("kafka.truststore.password")) {
          textField()
        }
        row {
          //ssl.endpoint.identification.algorithm
          checkBox(KafkaMessagesBundle.message("kafka.auth.enable.server.host.name.indetification"))
        }.topGap(TopGap.SMALL)



        row {
          sslUseKeystore = checkBox(KafkaMessagesBundle.message("kafka.ssl.use.keystore")).onChanged {
            updateVisibilityOfSslKeystore()
          }
        }.topGap(TopGap.SMALL)
        sslKeystoreGroup = rowsRange {
          row(KafkaMessagesBundle.message("kafka.keystore.location")) {
            textFieldWithBrowseButton(project = project,
                                      browseDialogTitle = KafkaMessagesBundle.message("kafka.truststore.location.dialog.title"))

          }
          row(KafkaMessagesBundle.message("kafka.keystore.password")) {
            textField()
          }
          row(KafkaMessagesBundle.message("kafka.key.password")) {
            textField()
          }
        }
      }

      row(propertiesEditor).topGap(TopGap.MEDIUM)
    }


    group(KafkaMessagesBundle.message("settings.registry.title")) {
      row(registryUrl).bottomGap(BottomGap.SMALL)

      row(KafkaMessagesBundle.message("kafka.auth.method.label")) {
        schemaAuth = comboBox(SchemaRegistryAuthType.values().toList(),
                              CustomListCellRenderer<SchemaRegistryAuthType> { it.title }).onChanged {
          updateSchemaRegistryAuth()
        }
      }
      indent {
        schemaBasicAuthGroup = rowsRange {
          schemaBasicLogin = row(KafkaMessagesBundle.message("kafka.username")) {
            textField()
          }
          schemaBasicPassword = row(KafkaMessagesBundle.message("kafka.password")) {
            passwordField()
          }
        }

        schemaBearerToken = row(KafkaMessagesBundle.message("kafka.token")) {
          textField()
        }
      }

      row(registryProperties).topGap(TopGap.SMALL)
    }



    panel {
      row {
        cell(tunnelField.getComponent())
      }
    }

    updateAuthStatus()
    updateSchemaRegistryAuth()
  }

  private fun updateAuthStatus() {
    val authType = sourceTypeChooser.getValue()
    propertiesFile.isVisible = authType == KafkaPropertySource.FILE
    implicitClientSettingsGroup.visible(authType == KafkaPropertySource.DIRECT)
    updateVisibilityOfAuth()
  }

  private fun validateBrokerNames(names: String): String? {
    if (names.isBlank())
      return KafkaMessagesBundle.message("settings.url.must.be.non.empty.hint")
    val brokers = names.split(",").map { it.trim() }
    val errors = brokers.map { it to BdtUrlUtils.validateUrl(it) }.filter { it.second != null }
    return errors.firstOrNull()?.let { "${it.first}: ${it.second ?: MessagesBundle.message("unexpected.error")}" }
  }

  private fun updateSchemaRegistryAuth() {
    val selectedAuthType = schemaAuth.component.item
    schemaBasicAuthGroup.visible(selectedAuthType == SchemaRegistryAuthType.BASIC_AUTH)
    schemaBearerToken.visible(selectedAuthType == SchemaRegistryAuthType.BEARER)
  }


  private fun updateVisibilityOfAuth() {
    val selectedAuthType = authType.component.item
    saslGroup.visible(selectedAuthType == KafkaSecurityType.SASL)
    sslGroup.visible(selectedAuthType == KafkaSecurityType.SSL)
    when (selectedAuthType) {
      KafkaSecurityType.NOT_SPECIFIED -> {}
      KafkaSecurityType.SASL -> {
        updateVisibilityOfSasl()
      }
      KafkaSecurityType.SSL -> {
        updateVisibilityOfSslKeystore()
      }
      KafkaSecurityType.AWS_IAM -> {}
      else -> {}
    }
  }

  private fun updateVisibilityOfSasl() {
    val mechanism = saslMechanism.component.item
    saslCredentialsGroup.visible(mechanism in setOf(KafkaSaslMechanism.PLAIN, KafkaSaslMechanism.SCRAM_256, KafkaSaslMechanism.SCRAM_512))
    saslKerberosGroup.visible(mechanism == KafkaSaslMechanism.KERBEROS)
  }

  private fun updateVisibilityOfSslKeystore() {
    val use = sslUseKeystore.component.isSelected
    sslKeystoreGroup.visible(use)
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}