package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.monitoring.TunnableSettingsCustomizer
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.settings.kerberos.BdtKerberosManager
import com.jetbrains.bigdatatools.common.settings.withValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.rfs.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import com.jetbrains.bigdatatools.kafka.util.jaas.BdtJaasConfig
import org.apache.kafka.clients.CommonClientConfigs.SECURITY_PROTOCOL_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_JAAS_CONFIG
import org.apache.kafka.common.config.SaslConfigs.SASL_MECHANISM
import org.apache.kafka.common.config.SslConfigs.*
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
    connectionData, uiDisposable).also { editor ->
    editor.getComponent().whenFocusLost {
      updateUiFromProperties()
    }
  }

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
  private lateinit var authMethod: Cell<ComboBox<KafkaAuthMethod>>
  private lateinit var saslGroup: RowsRange
  private lateinit var sslGroup: RowsRange
  private lateinit var saslCredentialsGroup: RowsRange
  private lateinit var saslKerberosGroup: RowsRange
  private lateinit var saslSecurityProtocol: Cell<ComboBox<SecurityProtocol>>
  private lateinit var saslMechanism: Cell<ComboBox<KafkaSaslMechanism>>
  private lateinit var sslUseKeystore: Cell<JBCheckBox>
  private lateinit var sslKeystoreGroup: RowsRange
  private lateinit var saslPrincipal: Cell<JBTextField>
  private lateinit var saslKeytab: Cell<TextFieldWithBrowseButton>
  private lateinit var saslUsername: Cell<JBTextField>
  private lateinit var saslPassword: Cell<JBTextField>
  private lateinit var sslTruststoreLocation: Cell<TextFieldWithBrowseButton>
  private lateinit var sslTruststorePassword: Cell<JBTextField>

  private lateinit var sslEnableValidateHostname: Cell<JBCheckBox>
  private lateinit var sslKeystoreLocation: Cell<TextFieldWithBrowseButton>
  private lateinit var sslKeystorePassword: Cell<JBTextField>
  private lateinit var sslKeyPassword: Cell<JBTextField>


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
        authMethod = comboBox(KafkaAuthMethod.values().toList(), CustomListCellRenderer<KafkaAuthMethod> { it.title }).onChanged {
          updateVisibilityOfAuth()
          updatePropertiesField()
        }
      }

      saslGroup = indent {
        row(KafkaMessagesBundle.message("kafka.security.protocol.label")) {
          saslSecurityProtocol = comboBox(listOf(SecurityProtocol.SASL_PLAINTEXT, SecurityProtocol.SASL_SSL),
                                          CustomListCellRenderer<SecurityProtocol> { it.name }).onChanged {
            updatePropertiesField()
          }
        }

        row(KafkaMessagesBundle.message("kafka.sasl.mechanism")) {
          saslMechanism = comboBox(KafkaSaslMechanism.values().toList(),
                                   CustomListCellRenderer<KafkaSaslMechanism> { it.title }).onChanged {
            updateVisibilityOfSasl()
            updatePropertiesField()
          }
        }

        saslKerberosGroup = indent {
          row(MessagesBundle.message("kerberos.settings.krb5.config.in.connection.label")) {
            @Suppress("DialogTitleCapitalization")
            val krb5Conf = textFieldWithBrowseButton(project = project,
                                                     browseDialogTitle = MessagesBundle.message(
                                                       "kerberos.connection.settings.krb5.select.dialog.title"))
            krb5Conf.text(BdtKerberosManager.instance.krb5Config)
            krb5Conf.onChanged {
              BdtKerberosManager.instance.krb5Config = krb5Conf.component.text
            }
          }

          row(MessagesBundle.message("kerberos.settings.principal.label")) {
            saslPrincipal = textField().onChanged {
              updatePropertiesField()
            }
          }
          row(MessagesBundle.message("kerberos.connection.settings.keytab.label")) {
            saslKeytab = textFieldWithBrowseButton(project = project,
                                                   browseDialogTitle = MessagesBundle.message(
                                                     "kerberos.connection.settings.keytab.select.dialog.title")).onChanged {
              updatePropertiesField()
            }
          }
        }


        saslCredentialsGroup = indent {
          row(KafkaMessagesBundle.message("kafka.username")) {
            saslUsername = textField().onChanged {
              updatePropertiesField()
            }
          }
          row(KafkaMessagesBundle.message("kafka.password")) {
            saslPassword = textField().onChanged {
              updatePropertiesField()
            }
          }
        }
      }


      sslGroup = indent {
        row(KafkaMessagesBundle.message("kafka.truststore.location")) {
          sslTruststoreLocation = textFieldWithBrowseButton(project = project,
                                                            browseDialogTitle = KafkaMessagesBundle.message(
                                                              "kafka.truststore.location.dialog.title")).onChanged {
            updatePropertiesField()
          }
        }
        row(KafkaMessagesBundle.message("kafka.truststore.password")) {
          sslTruststorePassword = textField().onChanged {
            updatePropertiesField()
          }
        }
        row {
          sslEnableValidateHostname = checkBox(KafkaMessagesBundle.message("kafka.auth.enable.server.host.name.indetification")).onChanged {
            updatePropertiesField()
          }
        }.topGap(TopGap.SMALL)



        row {
          sslUseKeystore = checkBox(KafkaMessagesBundle.message("kafka.ssl.use.keystore")).onChanged {
            updateVisibilityOfSslKeystore()
            updatePropertiesField()
          }
        }.topGap(TopGap.SMALL)
        sslKeystoreGroup = rowsRange {
          row(KafkaMessagesBundle.message("kafka.keystore.location")) {
            sslKeystoreLocation = textFieldWithBrowseButton(project = project,
                                                            browseDialogTitle = KafkaMessagesBundle.message(
                                                              "kafka.truststore.location.dialog.title")).onChanged {
              updatePropertiesField()
            }

          }
          row(KafkaMessagesBundle.message("kafka.keystore.password")) {
            sslKeystorePassword = textField().onChanged {
              updatePropertiesField()
            }
          }
          row(KafkaMessagesBundle.message("kafka.key.password")) {
            sslKeyPassword = textField().onChanged {
              updatePropertiesField()
            }
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
    updateUiFromProperties()
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
    val selectedAuthType = authMethod.component.item
    saslGroup.visible(selectedAuthType == KafkaAuthMethod.SASL)
    sslGroup.visible(selectedAuthType == KafkaAuthMethod.SSL)
    when (selectedAuthType) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        updateVisibilityOfSasl()
      }
      KafkaAuthMethod.SSL -> {
        updateVisibilityOfSslKeystore()
      }
      KafkaAuthMethod.AWS_IAM -> {}
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

  private fun updatePropertiesField() {
    val uiProps = getNullProperties() + getKafkaProperties()
    propertiesEditor.mergeConfig(uiProps)
  }

  private fun getNullProperties() = mapOf<String, String?>(
    SECURITY_PROTOCOL_CONFIG to null,
    SASL_MECHANISM to null,
    SASL_JAAS_CONFIG to null,
    SSL_TRUSTSTORE_LOCATION_CONFIG to null,
    SSL_TRUSTSTORE_PASSWORD_CONFIG to null,
    SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to null,
    SSL_KEYSTORE_LOCATION_CONFIG to null,
    SSL_KEYSTORE_PASSWORD_CONFIG to null,
    SSL_KEY_PASSWORD_CONFIG to null,
  )

  private fun getKafkaProperties(): Map<String, String> {
    val result = mutableMapOf<String, String?>()
    when (authMethod.component.item!!) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        val saslMechanism = saslMechanism.component.item

        val jaasConfig = if (saslMechanism == KafkaSaslMechanism.KERBEROS) {
          val keytab = saslKeytab.component.text
          val principal = saslPrincipal.component.text
          "${saslMechanism.module} required useKeyTab=true keyTab='$keytab' principal='$principal';"
        }
        else {
          "${saslMechanism.module} required username='${saslUsername.component.text}' password='${saslPassword.component.text}';"
        }
        result += mapOf(SECURITY_PROTOCOL_CONFIG to saslSecurityProtocol.component.item?.name,
                        SASL_MECHANISM to saslMechanism?.saslMechanism,
                        SASL_JAAS_CONFIG to jaasConfig)
      }
      KafkaAuthMethod.SSL -> {
        result += mapOf(SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                        SSL_TRUSTSTORE_LOCATION_CONFIG to sslTruststoreLocation.component.text.ifBlank { null },
                        SSL_TRUSTSTORE_PASSWORD_CONFIG to sslTruststorePassword.component.text.ifBlank { null })
        if (!sslEnableValidateHostname.component.isSelected)
          result += mapOf(SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "")
        if (sslUseKeystore.component.isEnabled) {
          result += mapOf(SSL_KEYSTORE_LOCATION_CONFIG to sslKeystoreLocation.component.text.ifBlank { null },
                          SSL_KEYSTORE_PASSWORD_CONFIG to sslKeystorePassword.component.text.ifBlank { null },
                          SSL_KEY_PASSWORD_CONFIG to sslKeyPassword.component.text.ifBlank { null })
        }
      }
      KafkaAuthMethod.AWS_IAM -> {}
    }
    return result.entries.associate { it.key to it.value!! }
  }

  private fun updateUiFromProperties() {
    setKafkaProperties(propertiesEditor.getProperties() ?: emptyMap())
  }

  private fun setKafkaProperties(properties: Map<String, String>) {
    val securityProtocol = properties[SECURITY_PROTOCOL_CONFIG]?.let {
      SecurityProtocol.values().firstOrNull { protocol -> protocol.name == it }
    } ?: SecurityProtocol.PLAINTEXT
    when (securityProtocol) {
      SecurityProtocol.PLAINTEXT -> {
        authMethod.component.item = KafkaAuthMethod.NOT_SPECIFIED
        return
      }
      SecurityProtocol.SASL_PLAINTEXT, SecurityProtocol.SASL_SSL -> {
        authMethod.component.item = KafkaAuthMethod.SASL
        saslSecurityProtocol.component.item = securityProtocol
        val saslMechanismValue = properties[SASL_MECHANISM]?.let { s -> KafkaSaslMechanism.values().firstOrNull { it.saslMechanism == s } }
                                 ?: return
        saslMechanism.component.item = saslMechanismValue
        val jaasConfig = properties[SASL_JAAS_CONFIG] ?: return
        val bdtJaasConfig = try {
          BdtJaasConfig(jaasConfig).config?.options?.map { it.key.lowercase() to (it.value?.toString() ?: "") }?.toMap() ?: return
        }
        catch (t: Throwable) {
          return
        }

        saslUsername.component.text = bdtJaasConfig["username"] ?: ""
        saslPassword.component.text = bdtJaasConfig["password"] ?: ""
        saslKeytab.component.text = bdtJaasConfig["keytab"] ?: ""
        saslPrincipal.component.text = bdtJaasConfig["principal"] ?: ""
      }
      SecurityProtocol.SSL -> {
        authMethod.component.item = KafkaAuthMethod.SSL

        sslTruststoreLocation.component.text = properties[SSL_TRUSTSTORE_LOCATION_CONFIG] ?: ""
        sslTruststorePassword.component.text = properties[SSL_TRUSTSTORE_PASSWORD_CONFIG] ?: ""
        sslKeystoreLocation.component.text = properties[SSL_KEYSTORE_LOCATION_CONFIG] ?: ""
        sslKeystorePassword.component.text = properties[SSL_KEYSTORE_PASSWORD_CONFIG] ?: ""
        sslKeyPassword.component.text = properties[SSL_KEY_PASSWORD_CONFIG] ?: ""
        sslEnableValidateHostname.component.isSelected = properties[SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] == ""
      }
    }
  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}