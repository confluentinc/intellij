package io.confluent.intellijplugin.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import io.confluent.intellijplugin.aws.connection.auth.AuthenticationType
import io.confluent.intellijplugin.core.connection.tunnel.ui.SshTunnelComponent
import io.confluent.intellijplugin.core.monitoring.TunnelableSettingsCustomizer
import io.confluent.intellijplugin.core.settings.ModificationKey
import io.confluent.intellijplugin.core.settings.connections.ConnectionData
import io.confluent.intellijplugin.core.settings.fields.*
import io.confluent.intellijplugin.core.settings.withValidator
import io.confluent.intellijplugin.core.ui.block
import io.confluent.intellijplugin.core.ui.components.ConnectionPropertiesEditor
import io.confluent.intellijplugin.core.ui.components.RadioComboBox
import io.confluent.intellijplugin.core.ui.row
import io.confluent.intellijplugin.core.util.BdtUrlUtils
import io.confluent.intellijplugin.registry.KafkaRegistryType
import io.confluent.intellijplugin.rfs.*
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import kotlinx.coroutines.CoroutineScope

class KafkaSettingsCustomizer(project: Project,
                              connectionData: KafkaConnectionData,
                              uiDisposable: Disposable,
                              coroutineScope: CoroutineScope) :
  TunnelableSettingsCustomizer<KafkaConnectionData>(connectionData, project, uiDisposable) {
  override val tunnelField: SshTunnelComponent<KafkaConnectionData> = SshTunnelComponent(project, uiDisposable, connectionData,
                                                                                         hostAndPortProvider,
                                                                                         enabledNotification = KafkaMessagesBundle.message(
                                                                                           "ssh.tunnel.enable.notification"))

  override val url = StringNamedField(ConnectionData::uri, ModificationKey(KafkaMessagesBundle.message("settings.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.url.text.empty")
      getTextComponent().toolTipText = KafkaMessagesBundle.message("settings.url.text.hint")
    }.withValidator(uiDisposable, ::validateBrokerNames) as StringNamedField

  internal val registryType = RadioGroupField(KafkaConnectionData::registryType,
                                              ModificationKey(KafkaMessagesBundle.message("schema.registry.type.label")), connectionData,
                                              KafkaRegistryType.entries)

  private val brokerSettings = KafkaBrokerSettings(project, connectionData, uiDisposable, coroutineScope, url, registryType)
  private val registrySettings = KafkaRegistrySettings(project, connectionData, uiDisposable, coroutineScope, registryType)

  internal lateinit var brokerConfSource: RadioGroupField<KafkaConnectionData, KafkaConfigurationSource>
  internal lateinit var brokerCloudSource: RadioGroupField<KafkaConnectionData, KafkaCloudType>
  internal lateinit var brokerPropertiesSource: RadioGroupField<KafkaConnectionData, KafkaPropertySource>
  internal lateinit var brokerPropertiesEditor: AbstractPropertiesFieldComponent<KafkaConnectionData>
  internal lateinit var brokerPropertiesFile: BrowseTextField<KafkaConnectionData>
  internal lateinit var brokerConfluentConf: ConnectionPropertiesEditor
  internal lateinit var brokerMskUrl: Cell<JBTextField>
  internal lateinit var brokerAuthType: RadioComboBox<KafkaAuthMethod>

  internal lateinit var brokerMskCloudAccessKey: Cell<JBTextField>
  internal lateinit var brokerMskCloudSecretKey: Cell<JBPasswordField>
  internal lateinit var brokerMskCloudAuthType: Cell<ComboBox<AuthenticationType>>
  internal lateinit var brokerMskCloudProfile: Cell<ComboBox<String>>

  internal lateinit var brokerAwsIamAccess: Cell<JBTextField>
  internal lateinit var brokerAwsIamSecretKey: Cell<JBPasswordField>
  internal lateinit var brokerAwsIamAuthType: Cell<ComboBox<AuthenticationType>>
  internal lateinit var brokerAwsIamProfile: Cell<ComboBox<String>>

  internal lateinit var brokerSaslKeytab: Cell<TextFieldWithBrowseButton>
  internal lateinit var brokerSaslMechanism: Cell<ComboBox<KafkaSaslMechanism>>
  internal lateinit var brokerSaslPassword: Cell<JBPasswordField>
  internal lateinit var brokerSaslPrincipal: Cell<JBTextField>
  internal lateinit var brokerSaslUsername: Cell<JBTextField>
  internal lateinit var brokerSaslUseTicketCache: Cell<JBCheckBox>
  internal lateinit var brokerSaslSecurityProtocol: Cell<JBCheckBox>

  internal lateinit var brokerSslKeyPassword: Cell<JBPasswordField>
  internal lateinit var brokerSslKeystorePassword: Cell<JBPasswordField>
  internal lateinit var brokerSslTruststorePassword: Cell<JBPasswordField>
  internal lateinit var brokerSslKeystoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var brokerSslTrustoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var brokerSslUseKeystore: Cell<JBCheckBox>
  internal lateinit var brokerSslEnableValidation: Cell<JBCheckBox>

  internal lateinit var registryConfluentUrl: WrappedTextComponent<KafkaConnectionData, *>
  internal lateinit var registryConfluentSource: RadioGroupField<KafkaConnectionData, KafkaConfigurationSource>
  internal lateinit var registryConfluentProperties: AbstractPropertiesFieldComponent<KafkaConnectionData>
  internal lateinit var registryConfluentAuth: RadioComboBox<SchemaRegistryAuthType>
  internal lateinit var registryConfluentBasicAuth: Cell<JBTextField>
  internal lateinit var registryConfluentBasicPassword: Cell<JBPasswordField>
  internal lateinit var registryConfluentBearerToken: Cell<JBTextField>
  internal lateinit var registryConfluentUseProxy: Cell<JBCheckBox>
  internal lateinit var registryConfluentProxyUrl: Cell<JBTextField>
  internal lateinit var registryConfluentUseBrokerSsl: Cell<JBCheckBox>
  internal lateinit var registryConfluentSslKeyPassword: Cell<JBPasswordField>
  internal lateinit var registryConfluentSslKeystorePassword: Cell<JBPasswordField>
  internal lateinit var registryConfluentSslTruststorePassword: Cell<JBPasswordField>
  internal lateinit var registryConfluentSslKeystoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var registryConfluentSslTrustoreLocation: Cell<TextFieldWithBrowseButton>
  internal lateinit var registryConfluentSslUseKeystore: Cell<JBCheckBox>
  internal lateinit var registryConfluentSslEnableValidation: Cell<JBCheckBox>

  internal lateinit var registryGlueAccessKey: Cell<JBTextField>
  internal lateinit var registryGlueSecretKey: Cell<JBPasswordField>
  internal lateinit var registryGlueAuthType: Cell<ComboBox<AuthenticationType>>
  internal lateinit var registryGlueProfile: Cell<ComboBox<String>>
  internal lateinit var registryGlueRegion: Cell<ComboBox<String>>
  internal lateinit var registryGlueRegistryName: LoadingChooserComponent<KafkaConnectionData>

  val schema = registrySettings.getDefaultFields()

  override fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> {
    return listOf<WrappedComponent<in KafkaConnectionData>>(nameField, url,
                                                            tunnelField) + brokerSettings.getDefaultFields() + registrySettings.getDefaultFields()
  }

  override fun getDefaultComponent(fields: List<WrappedComponent<in KafkaConnectionData>>, conn: KafkaConnectionData) = panel {
    row(nameField).topGap(TopGap.SMALL).bottomGap(BottomGap.SMALL)
    brokerSettings.setPanelComponent(this)
    registrySettings.setPanelComponent(this).visibleIf(brokerSettings.isRegistryVisible)

    block(tunnelField.getComponent()).topGap(TopGap.SMALL).visibleIf(brokerSettings.isRegistryVisible)
    initFields()
  }

  private fun validateBrokerNames(names: String): String? {
    if (names.isBlank())
      return KafkaMessagesBundle.message("settings.url.must.be.non.empty.hint")
    val brokers = names.split(",").map { it.trim() }
    val errors = brokers.map { it to BdtUrlUtils.validateUrl(it) }.filter { it.second != null }
    return errors.firstOrNull()?.let { "${it.first}: ${KafkaMessagesBundle.message("url.format.error")}" }
  }

  private fun initFields() {
    brokerConfSource = brokerSettings.confSource
    brokerCloudSource = brokerSettings.cloudSource
    brokerPropertiesSource = brokerSettings.propertiesSource
    brokerPropertiesEditor = brokerSettings.propertiesEditor
    brokerPropertiesFile = brokerSettings.propertiesFile
    brokerConfluentConf = brokerSettings.confluentSettings.confluentConf
    brokerMskUrl = brokerSettings.mskUrl
    brokerAuthType = brokerSettings.authMethod

    brokerMskCloudAccessKey = brokerSettings.awsMskCloudSettings.accessKey
    brokerMskCloudSecretKey = brokerSettings.awsMskCloudSettings.secretKey
    brokerMskCloudAuthType = brokerSettings.awsMskCloudSettings.authTypeChooser
    brokerMskCloudProfile = brokerSettings.awsMskCloudSettings.profileComboBox

    brokerAwsIamAccess = brokerSettings.awsMskAuthSettings.accessKey
    brokerAwsIamSecretKey = brokerSettings.awsMskAuthSettings.secretKey
    brokerAwsIamAuthType = brokerSettings.awsMskAuthSettings.authTypeChooser
    brokerAwsIamProfile = brokerSettings.awsMskAuthSettings.profileComboBox


    brokerSaslKeytab = brokerSettings.saslKeytab
    brokerSaslMechanism = brokerSettings.saslMechanism
    brokerSaslPassword = brokerSettings.saslPassword
    brokerSaslPrincipal = brokerSettings.saslPrincipal
    brokerSaslUsername = brokerSettings.saslUsername
    brokerSaslUseTicketCache = brokerSettings.saslKerberosUseTicketCache
    brokerSaslSecurityProtocol = brokerSettings.saslSecurityProtocol


    brokerSslKeyPassword = brokerSettings.sslComponent.sslKeyPassword
    brokerSslKeystorePassword = brokerSettings.sslComponent.sslKeystorePassword
    brokerSslTruststorePassword = brokerSettings.sslComponent.sslTruststorePassword
    brokerSslKeystoreLocation = brokerSettings.sslComponent.sslKeystoreLocation
    brokerSslTrustoreLocation = brokerSettings.sslComponent.sslTruststoreLocation
    brokerSslUseKeystore = brokerSettings.sslComponent.sslUseKeystore
    brokerSslEnableValidation = brokerSettings.sslComponent.sslEnableValidateHostname


    registryConfluentUrl = registrySettings.confluentUrl
    registryConfluentSource = registrySettings.confluentSource
    registryConfluentProperties = registrySettings.confluentPropertiesEditor
    registryConfluentAuth = registrySettings.confluentSchemaAuth
    registryConfluentBasicAuth = registrySettings.confluentBasicLogin
    registryConfluentBasicPassword = registrySettings.confluentBasicPassword
    registryConfluentBearerToken = registrySettings.confluentBearerToken
    registryConfluentUseProxy = registrySettings.confluentUseProxy
    registryConfluentProxyUrl = registrySettings.confluentProxyUrl
    registryConfluentUseBrokerSsl = registrySettings.confluentUseBrokerSsl
    registryConfluentSslKeyPassword = registrySettings.confluentSslComponent.sslKeyPassword
    registryConfluentSslKeystorePassword = registrySettings.confluentSslComponent.sslKeystorePassword
    registryConfluentSslTruststorePassword = registrySettings.confluentSslComponent.sslTruststorePassword
    registryConfluentSslKeystoreLocation = registrySettings.confluentSslComponent.sslKeystoreLocation
    registryConfluentSslTrustoreLocation = registrySettings.confluentSslComponent.sslTruststoreLocation
    registryConfluentSslUseKeystore = registrySettings.confluentSslComponent.sslUseKeystore
    registryConfluentSslEnableValidation = registrySettings.confluentSslComponent.sslEnableValidateHostname

    registryGlueAccessKey = registrySettings.awsGlueSettings.accessKey
    registryGlueSecretKey = registrySettings.awsGlueSettings.secretKey
    registryGlueAuthType = registrySettings.awsGlueSettings.authTypeChooser
    registryGlueProfile = registrySettings.awsGlueSettings.profileComboBox
    registryGlueRegion = registrySettings.awsGlueSettings.profileComboBox
    registryGlueRegistryName = registrySettings.glueRegistryName

  }

  object KafkaSettingsKeys {
    val PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val REGISTRY_PROPERTIES_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties"))
    val PROPERTIES_FILE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.properties.file"))
    val CONFIGURATION_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
    val CLOUD_PROVIDER = ModificationKey(KafkaMessagesBundle.message("settings.cloud.provider"))
    val PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("kafka.property.source.label"))
    val REGISTRY_PROPERTIES_SOURCE_KEY = ModificationKey(KafkaMessagesBundle.message("settings.property.source"))
  }
}