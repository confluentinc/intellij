package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.aws.connection.auth.AuthenticationType
import com.intellij.bigdatatools.aws.ui.external.AwsSettingsComponentForKafka
import com.intellij.bigdatatools.aws.ui.external.StaticAwsSettingsInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ContextHelpLabel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.settings.kerberos.BdtJaasConfig
import com.jetbrains.bigdatatools.common.settings.kerberos.KerberosSettingsDialog
import com.jetbrains.bigdatatools.common.settings.withEmptyOrFileExistValidator
import com.jetbrains.bigdatatools.common.ui.CustomListCellRenderer
import com.jetbrains.bigdatatools.common.ui.block
import com.jetbrains.bigdatatools.common.ui.components.RadioComboBox
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.common.util.PathUtils
import com.jetbrains.bigdatatools.kafka.rfs.*
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SaslConfigs.SASL_CLIENT_CALLBACK_HANDLER_CLASS
import org.apache.kafka.common.config.SaslConfigs.SASL_KERBEROS_SERVICE_NAME
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import java.util.concurrent.atomic.AtomicBoolean

class KafkaBrokerSettings(val project: Project,
                          val connectionData: KafkaConnectionData,
                          private val uiDisposable: Disposable,
                          val url: StringNamedField<ConnectionData>) {
  private val confSource = RadioGroupField(KafkaConnectionData::brokerConfigurationSource,
                                           KafkaSettingsCustomizer.KafkaSettingsKeys.CONFIGURATION_SOURCE_KEY,
                                           connectionData,
                                           KafkaConfigurationSource.values()).apply {
    addItemListener {
      updateConfVisibility()
    }
  }

  private val propertiesSource = RadioGroupField(KafkaConnectionData::propertySource,
                                                 KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_SOURCE_KEY,
                                                 connectionData,
                                                 KafkaPropertySource.values()).apply {
    addItemListener {
      onUpdatePropertiesSource()
    }
  }

  private val propertiesEditor = PropertiesFieldComponent.create(
    project,
    KafkaPropertiesUtils.getAdminPropertiesDescriptions(),
    KafkaConnectionData::properties,
    KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_KEY,
    connectionData, uiDisposable).also { editor ->
    editor.getComponent().whenFocusLost {
      setKafkaPropertiesToUi()
      updateVisibilityOfPropertiesKrb5Conf()
    }
  }

  private fun updateVisibilityOfPropertiesKrb5Conf() {
    val properties: Map<String, String> = propertiesEditor.getProperties() ?: emptyMap()
    val securityProtocol = getSecurityProtocol(properties)
    val isKrb5LinkActive = securityProtocol in setOf(SecurityProtocol.SASL_SSL, SecurityProtocol.SASL_PLAINTEXT) &&
                           getSaslMechanism(properties) == KafkaSaslMechanism.KERBEROS
    propertiesKerberosLinkRow.visible(isKrb5LinkActive)
  }

  private val propertiesFile = BrowseTextField(KafkaConnectionData::propertyFilePath,
                                               KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_FILE_KEY,
                                               connectionData,
                                               browseTitle = KafkaMessagesBundle.message("settings.properties.file.browse"),
                                               fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()).apply {
    withEmptyOrFileExistValidator(uiDisposable, canBeEmpty = false)
  }

  private lateinit var propertiesKerberosLinkRow: Row

  private lateinit var implicitClientSettingsGroup: RowsRange
  private lateinit var propertiesClientSettingsGroup: RowsRange

  private lateinit var directPropertiesGroup: Row
  private lateinit var filePropertiesGroup: Row

  private val authMethod = RadioComboBox(KafkaAuthMethod.values(), KafkaAuthMethod.NOT_SPECIFIED).apply {
    addItemListener {
      updateVisibilityOfAuth()
      updatePropertiesField()
    }
  }

  init {
    url.apply {
      getComponent().whenFocusLost {
        updatePropertiesField()
      }
    }
  }

  private lateinit var saslGroup: RowsRange
  private lateinit var sslGroup: RowsRange
  private lateinit var saslCredentialsGroup: RowsRange
  private lateinit var saslKerberosGroup: RowsRange
  private lateinit var saslAdditionalKerberosGroup: RowsRange
  private lateinit var saslSecurityProtocol: Cell<JBCheckBox>
  private lateinit var saslMechanism: Cell<ComboBox<KafkaSaslMechanism>>
  private lateinit var sslUseKeystore: Cell<JBCheckBox>
  private lateinit var sslKeystoreGroup: RowsRange
  private lateinit var saslPrincipal: Cell<JBTextField>
  private lateinit var saslKeytab: Cell<TextFieldWithBrowseButton>
  private lateinit var saslKerberosUseTicketCache: Cell<JBCheckBox>
  private lateinit var saslUsername: Cell<JBTextField>
  private lateinit var saslPassword: Cell<JBPasswordField>
  private lateinit var sslTruststoreLocation: Cell<TextFieldWithBrowseButton>
  private lateinit var sslTruststorePassword: Cell<JBPasswordField>

  private lateinit var sslEnableValidateHostname: Cell<JBCheckBox>
  private lateinit var sslKeystoreLocation: Cell<TextFieldWithBrowseButton>
  private lateinit var sslKeystorePassword: Cell<JBPasswordField>
  private lateinit var sslKeyPassword: Cell<JBPasswordField>

  private val awsMskSettings = AwsSettingsComponentForKafka {
    updatePropertiesField()
  }
  private lateinit var awsMskSettingsRows: RowsRange

  private var isUpdatedFromProperties = AtomicBoolean(false)

  fun setPanelComponent(panel: Panel) = panel.setComponent()

  private fun Panel.setComponent() {
    group(KafkaMessagesBundle.message("kafka.broker.group.title")) {
      row {
        label(confSource.labelComponent.text)
        cell(confSource.getComponent())
      }

      row(url)
      implicitClientSettingsGroup = createImplicitSettingsGroup()

      propertiesClientSettingsGroup = rowsRange {
        row {
          label(propertiesSource.labelComponent.text)
          cell(propertiesSource.getComponent())
        }

        filePropertiesGroup = row(propertiesFile)
        directPropertiesGroup = block(propertiesEditor.getComponent())

        propertiesKerberosLinkRow = row {
          link(MessagesBundle.message("kerberos.settings.open.button")) {
            KerberosSettingsDialog(project).showAndGet()
          }
        }

      }

    }

    updateConfVisibility()
  }

  private fun Panel.createImplicitSettingsGroup() = rowsRange {
    row {
      label(KafkaMessagesBundle.message("kafka.auth.method.label"))
      cell(authMethod.getComponent()).resizableColumn()
    }

    saslGroup = indent {
      row(KafkaMessagesBundle.message("kafka.sasl.mechanism")) {
        saslMechanism = comboBox(KafkaSaslMechanism.values().toList(),
                                 CustomListCellRenderer<KafkaSaslMechanism> { it.title }).align(AlignX.FILL).onChanged {
          updateVisibilityOfSasl()
          updatePropertiesField()
        }

        saslSecurityProtocol = checkBox(KafkaMessagesBundle.message("kafka.auth.sasl.use.ssl")).onChanged {
          updatePropertiesField()
        }
      }

      saslKerberosGroup = indent {
        row {
          saslKerberosUseTicketCache = checkBox(MessagesBundle.message("settings.use.kerberos.cache")).onChanged {
            updateVisibilityOfAdditionalKerberos()
            updatePropertiesField()
          }.gap(RightGap.SMALL)
          cell(ContextHelpLabel.create(MessagesBundle.message("kerberos.settings.use.ticket.cache.tooltip")))
        }
        saslAdditionalKerberosGroup = rowsRange {
          row(MessagesBundle.message("kerberos.settings.principal.label")) {
            saslPrincipal = textField().align(AlignX.FILL).onChanged {
              updatePropertiesField()
            }.apply {
              component.emptyText.text = MessagesBundle.message("kerberos.settings.principal.empty")
            }
          }
          row(MessagesBundle.message("kerberos.connection.settings.keytab.label")) {
            saslKeytab = textFieldWithBrowseButton(project = project,
                                                   browseDialogTitle = MessagesBundle.message(
                                                     "kerberos.connection.settings.keytab.select.dialog.title")) {
              PathUtils.toUnixPath(it.canonicalPath ?: "/")
            }.align(
              AlignX.FILL).onChanged {
              updatePropertiesField()
            }
          }
        }
        row {
          link(MessagesBundle.message("kerberos.settings.open.button")) {
            KerberosSettingsDialog(project).showAndGet()
          }
        }
      }

      saslCredentialsGroup = indent {
        row(KafkaMessagesBundle.message("kafka.username")) {
          saslUsername = textField().align(AlignX.FILL).onChanged {
            updatePropertiesField()
          }
        }
        row(KafkaMessagesBundle.message("kafka.password")) {
          saslPassword = passwordField().align(AlignX.FILL).onChanged {
            updatePropertiesField()
          }
        }
      }
    }

    sslGroup = indent {
      row {
        sslEnableValidateHostname = checkBox(
          KafkaMessagesBundle.message("kafka.auth.enable.server.host.name.indetification")).onChanged {
          updatePropertiesField()
        }
      }.bottomGap(BottomGap.SMALL)
      row(KafkaMessagesBundle.message("kafka.truststore.location")) {
        sslTruststoreLocation = textFieldWithBrowseButton(project = project,
                                                          browseDialogTitle = KafkaMessagesBundle.message(
                                                            "kafka.truststore.location.dialog.title")).align(AlignX.FILL).onChanged {
          updatePropertiesField()
        }
      }
      row(KafkaMessagesBundle.message("kafka.truststore.password")) {
        sslTruststorePassword = passwordField().align(AlignX.FILL).onChanged {
          updatePropertiesField()
        }
      }.bottomGap(BottomGap.SMALL)
      row {
        sslUseKeystore = checkBox(KafkaMessagesBundle.message("kafka.ssl.use.keystore")).onChanged {
          updatePropertiesField()
          updateVisibilityOfSslKeystore()
        }
      }.topGap(TopGap.SMALL)

      sslKeystoreGroup = rowsRange {
        row(KafkaMessagesBundle.message("kafka.keystore.location")) {
          sslKeystoreLocation = textFieldWithBrowseButton(project = project,
                                                          browseDialogTitle = KafkaMessagesBundle.message(
                                                            "kafka.truststore.location.dialog.title")).align(AlignX.FILL).onChanged {
            updatePropertiesField()
          }
        }
        row(KafkaMessagesBundle.message("kafka.keystore.password")) {
          sslKeystorePassword = passwordField().align(AlignX.FILL).onChanged {
            updatePropertiesField()
          }
        }
        row(KafkaMessagesBundle.message("kafka.key.password")) {
          sslKeyPassword = passwordField().align(AlignX.FILL).onChanged {
            updatePropertiesField()
          }
        }
      }
    }

    awsMskSettingsRows = indent { awsMskSettings.getComponentRows(this) }
  }

  private fun updatePropertiesField() {
    if (isUpdatedFromProperties.get())
      return

    val uiProps = getNullProperties() + getKafkaPropertiesFromUi()
    propertiesEditor.mergeConfig(uiProps)
    updateVisibilityOfPropertiesKrb5Conf()
  }

  private fun getNullProperties() = mapOf<String, String?>(
    CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to null,
    SaslConfigs.SASL_MECHANISM to null,
    SaslConfigs.SASL_JAAS_CONFIG to null,
    SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to null,
    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to null,
    SASL_CLIENT_CALLBACK_HANDLER_CLASS to null,
    SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to null,
    SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to null,
    SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to null,
    SslConfigs.SSL_KEY_PASSWORD_CONFIG to null)

  private fun getKafkaPropertiesFromUi(): Map<String, String?> {
    val result = mutableMapOf<String, String?>(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to url.getTextComponent().text)
    when (authMethod.selectedItem) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        val saslMechanism = saslMechanism.component.item

        if (saslMechanism == KafkaSaslMechanism.KERBEROS) {
          result += SASL_KERBEROS_SERVICE_NAME to "kafka"
        }
        val jaasConfig = if (saslMechanism == KafkaSaslMechanism.KERBEROS) {
          val keytab = saslKeytab.component.text
          val principal = saslPrincipal.component.text
          if (saslKerberosUseTicketCache.component.isSelected) {
            "${saslMechanism.module} required useTicketCache=true;"
          }
          else {
            "${saslMechanism.module} required useKeyTab=true keyTab='$keytab' principal='$principal';"
          }
        }
        else {
          "${saslMechanism.module} required username='${saslUsername.component.text}' password='${saslPassword.component.text}';"
        }
        val securityProtocol = if (saslSecurityProtocol.component.isSelected) {
          SecurityProtocol.SASL_SSL
        }
        else {
          SecurityProtocol.SASL_PLAINTEXT
        }
        result += mapOf(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to securityProtocol.name,
                        SaslConfigs.SASL_MECHANISM to saslMechanism?.saslMechanism,
                        SaslConfigs.SASL_JAAS_CONFIG to jaasConfig)
      }
      KafkaAuthMethod.SSL -> {
        @Suppress("DEPRECATION")
        result += mapOf(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL.name,
                        SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to sslTruststoreLocation.component.text.ifBlank { null },
                        SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to sslTruststorePassword.component.text.ifBlank { null })
        if (!sslEnableValidateHostname.component.isSelected)
          result += mapOf(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "")
        if (sslUseKeystore.component.isEnabled) {
          @Suppress("DEPRECATION")
          result += mapOf(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to sslKeystoreLocation.component.text.ifBlank { null },
                          SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to sslKeystorePassword.component.text.ifBlank { null },
                          SslConfigs.SSL_KEY_PASSWORD_CONFIG to sslKeyPassword.component.text.ifBlank { null })
        }
      }
      KafkaAuthMethod.AWS_IAM -> {
        val info = awsMskSettings.getInfo()
        val jaasConfig = when (info.authenticationType) {
          AuthenticationType.KEY_PAIR.id, AuthenticationType.DEFAULT.id -> "software.amazon.msk.auth.iam.IAMLoginModule required ;"
          AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE.id -> "software.amazon.msk.auth.iam.IAMLoginModule required awsProfileName='${info.profile ?: "<NOT_SELECTED>"}';"
          else -> null
        }
        result += mapOf(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SASL_SSL.name,
                        SaslConfigs.SASL_MECHANISM to AwsSettingsComponentForKafka.AWS_MECHANISM,
                        SaslConfigs.SASL_JAAS_CONFIG to jaasConfig,
                        SASL_CLIENT_CALLBACK_HANDLER_CLASS to "software.amazon.msk.auth.iam.IAMClientCallbackHandler",
                        AwsSettingsComponentForKafka.AWS_ACCESS_KEY to info.accessKey,
                        AwsSettingsComponentForKafka.AWS_SECRET_KEY to info.secretKey)
      }
    }
    return result.entries.associate { it.key to it.value }
  }

  private fun setKafkaPropertiesToUi() {
    try {
      isUpdatedFromProperties.set(true)
      val properties: Map<String, String> = propertiesEditor.getProperties() ?: emptyMap()
      properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]?.let {
        url.getTextComponent().text = it
      }
      val securityProtocol = getSecurityProtocol(properties) ?: SecurityProtocol.PLAINTEXT
      when (securityProtocol) {
        SecurityProtocol.PLAINTEXT -> {
          authMethod.selectedItem = KafkaAuthMethod.NOT_SPECIFIED
          return
        }
        SecurityProtocol.SASL_PLAINTEXT, SecurityProtocol.SASL_SSL -> {
          if (properties[SaslConfigs.SASL_MECHANISM] == AwsSettingsComponentForKafka.AWS_MECHANISM) {
            setAwsProperties(properties)
            return
          }
          authMethod.selectedItem = KafkaAuthMethod.SASL
          saslSecurityProtocol.component.isSelected = securityProtocol == SecurityProtocol.SASL_SSL
          val saslMechanismValue = getSaslMechanism(properties)
          saslMechanismValue ?: return
          saslMechanism.component.item = saslMechanismValue
          val jaasConfig = properties[SaslConfigs.SASL_JAAS_CONFIG] ?: return
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
          saslKerberosUseTicketCache.component.isSelected = bdtJaasConfig["useticketcache"]?.toBoolean() ?: false
        }
        SecurityProtocol.SSL -> {
          authMethod.selectedItem = KafkaAuthMethod.SSL

          sslTruststoreLocation.component.text = properties[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] ?: ""
          sslTruststorePassword.component.text = properties[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] ?: ""
          sslKeystoreLocation.component.text = properties[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] ?: ""
          sslKeystorePassword.component.text = properties[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] ?: ""
          sslKeyPassword.component.text = properties[SslConfigs.SSL_KEY_PASSWORD_CONFIG] ?: ""
          sslEnableValidateHostname.component.isSelected = properties[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] != ""
        }
      }
    }
    finally {
      isUpdatedFromProperties.set(false)
    }
  }


  private fun setAwsProperties(properties: Map<String, String>) {
    authMethod.selectedItem = KafkaAuthMethod.AWS_IAM

    val jaasConfig = properties[SaslConfigs.SASL_JAAS_CONFIG] ?: return
    val bdtJaasConfig = try {
      BdtJaasConfig(jaasConfig).config?.options?.map { it.key.lowercase() to (it.value?.toString() ?: "") }?.toMap() ?: return
    }
    catch (t: Throwable) {
      return
    }
    val profile = bdtJaasConfig["awsProfileName"]

    val secretKey = properties[AwsSettingsComponentForKafka.AWS_SECRET_KEY]
    val accessKey = properties[AwsSettingsComponentForKafka.AWS_ACCESS_KEY]
    val authType = when {
      profile == null && accessKey == null && secretKey == null -> AuthenticationType.DEFAULT
      profile != null -> AuthenticationType.PROFILE_FROM_CREDENTIALS_FILE
      else -> AuthenticationType.KEY_PAIR
    }

    val info = StaticAwsSettingsInfo(
      authType.id,
      profile = profile,
      accessKey = accessKey,
      secretKey = secretKey,
      region = null,
    )
    awsMskSettings.loadInfo(info)
  }

  private fun updateConfVisibility() {
    implicitClientSettingsGroup.visible(confSource.getValue() == KafkaConfigurationSource.FROM_UI)
    propertiesClientSettingsGroup.visible(confSource.getValue() == KafkaConfigurationSource.FROM_PROPERTIES)

    updateUrlVisibility()

    when (confSource.getValue()) {
      KafkaConfigurationSource.FROM_UI -> {
        setKafkaPropertiesToUi()
        updateVisibilityOfAuth()
      }
      KafkaConfigurationSource.FROM_PROPERTIES -> {
        onUpdatePropertiesSource()
      }
    }
  }

  private fun onUpdatePropertiesSource() {
    directPropertiesGroup.visible(propertiesSource.getValue() == KafkaPropertySource.DIRECT)
    filePropertiesGroup.visible(propertiesSource.getValue() == KafkaPropertySource.FILE)
    updateUrlVisibility()
  }

  private fun updateUrlVisibility() {
    url.isVisible = confSource.getValue() == KafkaConfigurationSource.FROM_UI || propertiesSource.getValue() == KafkaPropertySource.DIRECT
  }

  private fun updateVisibilityOfAuth() {
    val selectedAuthType = authMethod.selectedItem
    saslGroup.visible(selectedAuthType == KafkaAuthMethod.SASL)
    sslGroup.visible(selectedAuthType == KafkaAuthMethod.SSL)
    awsMskSettingsRows.visible(selectedAuthType == KafkaAuthMethod.AWS_IAM)
    when (selectedAuthType) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        updateVisibilityOfSasl()
      }
      KafkaAuthMethod.SSL -> {
        updateVisibilityOfSslKeystore()
      }
      KafkaAuthMethod.AWS_IAM -> {
        awsMskSettings.updateVisibility()
      }
    }
  }

  private fun updateVisibilityOfSasl() {
    val mechanism = saslMechanism.component.item
    saslCredentialsGroup.visible(mechanism in setOf(KafkaSaslMechanism.PLAIN, KafkaSaslMechanism.SCRAM_256, KafkaSaslMechanism.SCRAM_512))
    saslKerberosGroup.visible(mechanism == KafkaSaslMechanism.KERBEROS)
    if (mechanism == KafkaSaslMechanism.KERBEROS) {
      updateVisibilityOfAdditionalKerberos()
    }
  }

  private fun updateVisibilityOfAdditionalKerberos() {
    saslAdditionalKerberosGroup.visible(!saslKerberosUseTicketCache.component.isSelected)
  }

  private fun updateVisibilityOfSslKeystore() {
    val use = sslUseKeystore.component.isSelected
    sslKeystoreGroup.visible(use)
  }

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> = listOf(propertiesEditor, propertiesFile,
                                                                                  propertiesSource, confSource)

  private fun getSaslMechanism(properties: Map<String, String>): KafkaSaslMechanism? {
    val saslMechanismKey = properties[SaslConfigs.SASL_MECHANISM] ?: SaslConfigs.DEFAULT_SASL_MECHANISM
    return KafkaSaslMechanism.values().firstOrNull { it.saslMechanism == saslMechanismKey }
  }

  private fun getSecurityProtocol(properties: Map<String, String>): SecurityProtocol? {
    return properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]?.let {
      SecurityProtocol.values().firstOrNull { protocol -> protocol.name == it }
    }
  }
}