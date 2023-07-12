package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.bigdatatools.aws.connection.auth.AuthenticationType
import com.intellij.bigdatatools.aws.ui.external.AwsSettingsComponentForKafka
import com.intellij.bigdatatools.aws.ui.external.StaticAwsSettingsInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
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
import com.jetbrains.bigdatatools.common.ui.revalidateOnLinesChanged
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.common.util.PathUtils
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
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
                          val url: StringNamedField<ConnectionData>,
                          val registryType: RadioGroupField<KafkaConnectionData, KafkaRegistryType>) {
  val isRegistryVisible = AtomicBooleanProperty(true)

  internal val confSource = RadioGroupField(KafkaConnectionData::brokerConfigurationSource,
                                           KafkaSettingsCustomizer.KafkaSettingsKeys.CONFIGURATION_SOURCE_KEY,
                                           connectionData,
                                           KafkaConfigurationSource.values()).apply {
    addItemListener {
      updateConfVisibility()
    }
  }

  internal val cloudSource = RadioGroupField(KafkaConnectionData::brokerCloudSource,
                                            KafkaSettingsCustomizer.KafkaSettingsKeys.CLOUD_PROVIDER,
                                            connectionData,
                                            KafkaCloudType.values()).apply {
    addItemListener {
      updateCloudVisibility()
    }
  }

  internal val propertiesSource = RadioGroupField(KafkaConnectionData::propertySource,
                                                  KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_SOURCE_KEY,
                                                  connectionData,
                                                  KafkaPropertySource.values()).apply {
    addItemListener {
      onUpdatePropertiesSource()
    }
  }

  internal val propertiesEditor = PropertiesFieldComponent.create(
    project,
    KafkaPropertiesUtils.getAdminPropertiesDescriptions(),
    KafkaConnectionData::properties,
    KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_KEY,
    connectionData, uiDisposable).also { editor ->
    editor.getComponent().whenFocusLost {
      setKafkaPropertiesToUi()
      updateVisibilityOfPropertiesKrb5Conf()
    }
  }.apply {
    getComponent().revalidateOnLinesChanged()
  }

  private fun updateVisibilityOfPropertiesKrb5Conf() {
    val properties: Map<String, String> = propertiesEditor.getProperties() ?: emptyMap()
    val securityProtocol = getSecurityProtocol(properties)
    val isKrb5LinkActive = securityProtocol in setOf(SecurityProtocol.SASL_SSL, SecurityProtocol.SASL_PLAINTEXT) &&
                           getSaslMechanism(properties) == KafkaSaslMechanism.KERBEROS
    propertiesKerberosLinkRow.visible(isKrb5LinkActive)
  }

  internal val propertiesFile = BrowseTextField(KafkaConnectionData::propertyFilePath,
                                                KafkaSettingsCustomizer.KafkaSettingsKeys.PROPERTIES_FILE_KEY,
                                                connectionData,
                                                browseTitle = KafkaMessagesBundle.message("settings.properties.file.browse"),
                                                fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFileDescriptor()).apply {
    withEmptyOrFileExistValidator(uiDisposable, canBeEmpty = false)
  }


  internal val confluentSettings = KafkaConfluentSettings(project, connectionData, uiDisposable, url, propertiesEditor, this)
  private lateinit var propertiesKerberosLinkRow: Row

  private lateinit var cloudGroup: RowsRange
  private lateinit var confluentGroup: RowsRange
  private lateinit var awsMskGroup: RowsRange
  internal lateinit var mskUrl: Cell<JBTextField>
  private lateinit var implicitClientSettingsGroup: RowsRange
  private lateinit var propertiesClientSettingsGroup: RowsRange

  private lateinit var directPropertiesGroup: Row
  private lateinit var filePropertiesGroup: Row

  internal val authMethod = RadioComboBox(KafkaAuthMethod.values(), KafkaAuthMethod.NOT_SPECIFIED).apply {
    addItemListener {
      updateVisibilityOfAuth()
      updatePropertiesField()
    }
  }

  internal val sslComponent = KafkaSslSettingsComponent(project, ::updatePropertiesField)

  init {
    url.apply {
      getComponent().whenFocusLost {
        updatePropertiesField()
      }
    }
  }

  private lateinit var saslGroup: RowsRange
  private lateinit var sslGroup: RowsRange
  private lateinit var sslGroupTitle: Row
  private lateinit var saslCredentialsGroup: RowsRange
  private lateinit var saslKerberosGroup: RowsRange
  private lateinit var saslAdditionalKerberosGroup: RowsRange
  internal lateinit var saslSecurityProtocol: Cell<JBCheckBox>
  internal lateinit var saslMechanism: Cell<ComboBox<KafkaSaslMechanism>>
  internal lateinit var saslPrincipal: Cell<JBTextField>
  internal lateinit var saslKeytab: Cell<TextFieldWithBrowseButton>
  internal lateinit var saslKerberosUseTicketCache: Cell<JBCheckBox>
  internal lateinit var saslUsername: Cell<JBTextField>
  internal lateinit var saslPassword: Cell<JBPasswordField>

  internal val awsMskCloudSettings = AwsSettingsComponentForKafka {
    updatePropertiesField()
  }


  internal val awsMskAuthSettings = AwsSettingsComponentForKafka {
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

      cloudGroup = rowsRange {
        row {
          label(cloudSource.labelComponent.text)
          cell(cloudSource.getComponent())
        }
        confluentGroup = confluentSettings.setPanelComponent(this)
        awsMskGroup = rowsRange {
          row {
            label(KafkaMessagesBundle.message("settings.url"))
            mskUrl = textField().resizableColumn().align(AlignX.FILL)
            mskUrl.text(propertiesEditor.getProperties()?.get(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG) ?: "")
            mskUrl.onChanged {
              url.getTextComponent().text = it.text
              updatePropertiesField()
            }
            contextHelp(KafkaMessagesBundle.message("settings.msk.setup.desc"),
                        KafkaMessagesBundle.message("settings.cloud.setup.title")).align(AlignX.RIGHT)
          }
          awsMskCloudSettings.getComponentRows(this)
        }
      }

      row {
        cell(url.labelComponent)
        cell(url.getComponent()).align(AlignX.FILL)
      }
      implicitClientSettingsGroup = createImplicitSettingsGroup()

      propertiesClientSettingsGroup = rowsRange {
        row {
          label(propertiesSource.labelComponent.text)
          cell(propertiesSource.getComponent())
        }

        filePropertiesGroup = row(propertiesFile)
        directPropertiesGroup = block(propertiesEditor.getComponent()).resizableRow()

        propertiesKerberosLinkRow = row {
          link(MessagesBundle.message("kerberos.settings.open.button")) {
            KerberosSettingsDialog(project).showAndGet()
          }
        }
        updateVisibilityOfPropertiesKrb5Conf()
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
          updateVisibilityOfAuth()
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

    sslGroupTitle = group(KafkaMessagesBundle.message("border.title.ssl.settings")) {}.bottomGap(BottomGap.NONE)
    sslGroup = sslComponent.create(this)
    awsMskSettingsRows = indent { awsMskAuthSettings.getComponentRows(this) }
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
    if (confSource.getValue() == KafkaConfigurationSource.CLOUD && cloudSource.getValue() == KafkaCloudType.AWS_MSK) {
      setAwsSettings(result, awsMskCloudSettings)
      result += CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to mskUrl.component.text
      return result
    }

    when (authMethod.selectedItem) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        val saslMechanism = saslMechanism.component.item

        if (saslMechanism == KafkaSaslMechanism.KERBEROS) {
          result += SASL_KERBEROS_SERVICE_NAME to "kafka"
        }
        @Suppress("DEPRECATION")
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

        if (securityProtocol == SecurityProtocol.SASL_SSL)
          addSslProperties(result)

      }
      KafkaAuthMethod.SSL -> {
        result[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = SecurityProtocol.SSL.name
        addSslProperties(result)
      }
      KafkaAuthMethod.AWS_IAM -> {
        setAwsSettings(result, awsMskAuthSettings)
      }
    }
    return result.entries.associate { it.key to it.value }
  }

  private fun setAwsSettings(result: MutableMap<String, String?>, awsMskAuthSettings: AwsSettingsComponentForKafka) {
    val info = awsMskAuthSettings.getInfo()
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

  private fun addSslProperties(result: MutableMap<String, String?>) {
    val config = sslComponent.getConfig()
    result += mapOf(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to config.truststoreLocation.ifBlank { null },
                    SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to config.truststorePassword.ifBlank { null })
    if (!config.validateHostName)
      result += mapOf(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "")
    if (config.useKeyStore) {
      result += mapOf(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to config.keystoreLocation.ifBlank { null },
                      SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to config.keystorePassword.ifBlank { null },
                      SslConfigs.SSL_KEY_PASSWORD_CONFIG to config.keyPassword.ifBlank { null })
    }
  }

  private fun setKafkaPropertiesToUi() {
    try {
      isUpdatedFromProperties.set(true)
      val properties: Map<String, String> = propertiesEditor.getProperties() ?: emptyMap()

      if (confSource.getValue() == KafkaConfigurationSource.CLOUD && cloudSource.getValue() == KafkaCloudType.AWS_MSK) {
        setAwsProperties(properties, awsMskCloudSettings)
        properties[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]?.let {
          mskUrl.component.text = it
        }
        return
      }

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
            setAwsProperties(properties, awsMskAuthSettings)
            return
          }
          authMethod.selectedItem = KafkaAuthMethod.SASL
          setSaslToUi(securityProtocol, properties)
        }
        SecurityProtocol.SSL -> {
          authMethod.selectedItem = KafkaAuthMethod.SSL

        }
      }
      setSslToUi(properties)
    }
    finally {
      isUpdatedFromProperties.set(false)
    }
  }

  private fun setSaslToUi(securityProtocol: SecurityProtocol, properties: Map<String, String>): Boolean {
    saslSecurityProtocol.component.isSelected = securityProtocol == SecurityProtocol.SASL_SSL
    val saslMechanismValue = getSaslMechanism(properties)
    saslMechanismValue ?: return true
    saslMechanism.component.item = saslMechanismValue
    val jaasConfig = properties[SaslConfigs.SASL_JAAS_CONFIG] ?: return true
    val bdtJaasConfig = try {
      BdtJaasConfig(jaasConfig).config?.options?.map { it.key.lowercase() to (it.value?.toString() ?: "") }?.toMap() ?: return true
    }
    catch (t: Throwable) {
      return true
    }

    saslUsername.component.text = bdtJaasConfig["username"] ?: ""
    saslPassword.component.text = bdtJaasConfig["password"] ?: ""
    saslKeytab.component.text = bdtJaasConfig["keytab"] ?: ""
    saslPrincipal.component.text = bdtJaasConfig["principal"] ?: ""
    saslKerberosUseTicketCache.component.isSelected = bdtJaasConfig["useticketcache"]?.toBoolean() ?: false
    return false
  }

  private fun setSslToUi(properties: Map<String, String>) {
    val keystoreLocation = properties[SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] ?: ""
    sslComponent.applyConfig(
      KafkaSslConfig(
        validateHostName = properties[SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] != "",
        truststoreLocation = properties[SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] ?: "",
        truststorePassword = properties[SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] ?: "",
        useKeyStore = keystoreLocation.isNotBlank(),
        keyPassword = properties[SslConfigs.SSL_KEY_PASSWORD_CONFIG] ?: "",
        keystoreLocation = keystoreLocation,
        keystorePassword = properties[SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] ?: ""
      )
    )
  }

  private fun setAwsProperties(properties: Map<String, String>, settingsUi: AwsSettingsComponentForKafka) {
    authMethod.selectedItem = KafkaAuthMethod.AWS_IAM

    val jaasConfig = properties[SaslConfigs.SASL_JAAS_CONFIG] ?: return
    val bdtJaasConfig = try {
      BdtJaasConfig(jaasConfig).config?.options?.map { it.key.lowercase() to (it.value?.toString() ?: "") }?.toMap() ?: return
    }
    catch (t: Throwable) {
      return
    }
    val profile = bdtJaasConfig["awsprofilename"]

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
    settingsUi.loadInfo(info)
  }

  private fun updateConfVisibility() {
    cloudGroup.visible(confSource.getValue() == KafkaConfigurationSource.CLOUD)
    implicitClientSettingsGroup.visible(confSource.getValue() == KafkaConfigurationSource.FROM_UI)
    propertiesClientSettingsGroup.visible(confSource.getValue() == KafkaConfigurationSource.FROM_PROPERTIES)

    updateUrlVisibility()

    isRegistryVisible.set(isSchemaVisible())

    when (confSource.getValue()) {
      KafkaConfigurationSource.FROM_UI -> {
        setKafkaPropertiesToUi()
        updateVisibilityOfAuth()
      }
      KafkaConfigurationSource.FROM_PROPERTIES -> {
        onUpdatePropertiesSource()
      }
      KafkaConfigurationSource.CLOUD -> {
        updateCloudVisibility()
        setKafkaPropertiesToUi()
      }
    }
  }

  private fun updateCloudVisibility() {
    confluentGroup.visible(cloudSource.getValue() == KafkaCloudType.CONFLUENT)
    awsMskGroup.visible(cloudSource.getValue() == KafkaCloudType.AWS_MSK)
    if (cloudSource.getValue() == KafkaCloudType.AWS_MSK) {
      awsMskCloudSettings.updateVisibility()
    }
    isRegistryVisible.set(isSchemaVisible())
  }

  private fun onUpdatePropertiesSource() {
    directPropertiesGroup.visible(propertiesSource.getValue() == KafkaPropertySource.DIRECT)
    filePropertiesGroup.visible(propertiesSource.getValue() == KafkaPropertySource.FILE)
    updateUrlVisibility()
  }

  private fun updateUrlVisibility() {
    url.isVisible = confSource.getValue() == KafkaConfigurationSource.FROM_UI ||
                    (propertiesSource.getValue() == KafkaPropertySource.DIRECT && confSource.getValue() == KafkaConfigurationSource.FROM_PROPERTIES)
  }

  private fun updateVisibilityOfAuth() {
    val selectedAuthType = authMethod.selectedItem
    val isSaslSsl = selectedAuthType == KafkaAuthMethod.SASL && saslSecurityProtocol.component.isSelected
    val isSslVisible = selectedAuthType == KafkaAuthMethod.SSL || isSaslSsl

    saslGroup.visible(selectedAuthType == KafkaAuthMethod.SASL)
    sslGroup.visible(isSslVisible)
    sslGroupTitle.visible(isSaslSsl)
    awsMskSettingsRows.visible(selectedAuthType == KafkaAuthMethod.AWS_IAM)
    when (selectedAuthType) {
      KafkaAuthMethod.NOT_SPECIFIED -> {}
      KafkaAuthMethod.SASL -> {
        updateVisibilityOfSasl()
      }
      KafkaAuthMethod.SSL -> {
      }
      KafkaAuthMethod.AWS_IAM -> {
        awsMskAuthSettings.updateVisibility()
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

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> = listOf(propertiesEditor, propertiesFile,
                                                                                  propertiesSource, confSource,
                                                                                  cloudSource) + confluentSettings.getDefaultFields()

  private fun getSaslMechanism(properties: Map<String, String>): KafkaSaslMechanism? {
    val saslMechanismKey = properties[SaslConfigs.SASL_MECHANISM] ?: SaslConfigs.DEFAULT_SASL_MECHANISM
    return KafkaSaslMechanism.values().firstOrNull { it.saslMechanism == saslMechanismKey }
  }

  private fun getSecurityProtocol(properties: Map<String, String>) =
    properties[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG]?.let {
      SecurityProtocol.values().firstOrNull { protocol -> protocol.name == it }
    }

  private fun isSchemaVisible(): Boolean =
    confSource.getValue() != KafkaConfigurationSource.CLOUD || cloudSource.getValue() != KafkaCloudType.CONFLUENT
}