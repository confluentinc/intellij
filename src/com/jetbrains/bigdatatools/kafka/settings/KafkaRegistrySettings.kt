package com.jetbrains.bigdatatools.kafka.settings

import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import com.intellij.bigdatatools.aws.connection.auth.AuthenticationType
import com.intellij.bigdatatools.aws.settings.AwsCompatibleConnectionData
import com.intellij.bigdatatools.aws.ui.external.AwsSettingsComponentForKafka
import com.intellij.bigdatatools.aws.ui.external.StaticAwsSettingsInfo
import com.intellij.bigdatatools.aws.utils.AwsSettingsConst
import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.not
import com.jetbrains.bigdatatools.common.serializer.BdtJson
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.ui.*
import com.jetbrains.bigdatatools.common.ui.components.RadioComboBox
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConfigurationSource
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.SchemaRegistryAuthType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import com.jetbrains.bigdatatools.kafka.util.KafkaSslUtils
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import kotlinx.coroutines.CoroutineScope
import org.apache.kafka.common.config.SslConfigs
import java.util.concurrent.atomic.AtomicBoolean

class KafkaRegistrySettings(val project: Project,
                            val connectionData: KafkaConnectionData,
                            uiDisposable: Disposable,
                            coroutineScope: CoroutineScope,
                            val registryType: RadioGroupField<KafkaConnectionData, KafkaRegistryType>) {

  private val confluentPropertiesCredentialsHolder = CredentialsHolder(connectionData, KafkaConnectionData.CONFIG_REGISTRY_KEY,
                                                                       uiDisposable, coroutineScope)

  internal val confluentPropertiesEditor = SecretPropertiesFieldComponent(
    project,
    KafkaPropertiesUtils.getRegistryPropertiesDescriptions(),
    confluentPropertiesCredentialsHolder,
    KafkaSettingsCustomizer.KafkaSettingsKeys.REGISTRY_PROPERTIES_KEY,
    connectionData,
    uiDisposable
  )
    .apply {
      var isInitialized = false
      getComponent().doOnChange {
        if (isInitialized || getComponent().text.isBlank())
          return@doOnChange
        isInitialized = true
        updateRegistryUiFromProperties()
      }

      getComponent().whenFocusLost {
        updateRegistryUiFromProperties()
      }

      getComponent().revalidateOnLinesChanged()
    }

  internal val confluentSource = RadioGroupField(KafkaConnectionData::registryConfSource,
                                                 KafkaSettingsCustomizer.KafkaSettingsKeys.REGISTRY_PROPERTIES_SOURCE_KEY,
                                                 connectionData,
                                                 listOf(KafkaConfigurationSource.FROM_UI,
                                                        KafkaConfigurationSource.FROM_PROPERTIES)).apply {
    addItemListener {
      updateRegistryAuthStatus()
    }
  }

  internal val confluentUrl = StringNonRequiredField(
    KafkaConnectionData::registryUrl,
    ModificationKey(KafkaMessagesBundle.message("settings.registry.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.registry.url.hint")
    }
    .also { editor ->
      editor.getComponent().whenFocusLost {
        updateRegistryPropertiesField()
      }
    }

  internal val confluentSchemaAuth = RadioComboBox(SchemaRegistryAuthType.entries.toTypedArray(),
                                                   SchemaRegistryAuthType.NOT_SPECIFIED).apply {
    addItemListener {
      updateSchemaRegistryAuth()
      updateRegistryPropertiesField()
    }
  }

  private val awsCredentials = CredentialsHolder(connectionData, AwsCompatibleConnectionData.SECRET_KEY_ID, uiDisposable, coroutineScope)

  private val awsAccessKey = UsernameNamedField(AwsSettingsConst.S3_ACCESS_KEY, awsCredentials)
  private val awsSecretKey = PasswordNamedField(AwsSettingsConst.S3_SECRET_KEY, awsCredentials)

  private val glueSettings = StringNonRequiredField(
    KafkaConnectionData::glueSettings,
    ModificationKey("GlueSettings"), connectionData)

  internal val glueRegistryName = LoadingChooserComponent(
    KafkaConnectionData::glueRegistryName,
    ModificationKey(KafkaMessagesBundle.message("settings.glue.registry.name")),
    connectionData,
    AWSSchemaRegistryConstants.DEFAULT_REGISTRY_NAME,
    coroutineScope,
    isEditable = true,
  ) {
    KafkaUIUtils.showAndGetGlueRegistry(project, awsGlueSettings.getInfo())
  }

  private val useBrokerSslCheckbox = CheckBoxField(KafkaConnectionData::registryUseBrokerSsl, USE_BROKER_SSL,
                                                   connectionData)

  internal val awsGlueSettings = AwsSettingsComponentForKafka(includeRegionSetting = true) {
    saveGlueSettings()
  }

  internal val confluentSslComponent = KafkaSslSettingsComponent(project, ::updateRegistryPropertiesField)

  private lateinit var confluentGroup: RowsRange
  private lateinit var glueGroup: RowsRange

  private lateinit var registryPropertiesGroup: Row
  private lateinit var implicitRegistryClientSettingsGroup: RowsRange

  private lateinit var schemaBasicAuthGroup: RowsRange
  private lateinit var schemaBearerGroup: Row
  internal lateinit var confluentBasicLogin: Cell<JBTextField>
  internal lateinit var confluentBasicPassword: Cell<JBPasswordField>
  internal lateinit var confluentBearerToken: Cell<JBTextField>

  internal lateinit var confluentUseProxy: Cell<JBCheckBox>
  internal lateinit var confluentProxyUrl: Cell<JBTextField>
  internal lateinit var confluentUseBrokerSsl: Cell<JBCheckBox>

  private val isUpdatingFromProperties = AtomicBoolean(false)

  init {
    registryType.apply {
      addItemListener {
        onRegistryTypeChanged()
      }
    }
  }

  fun setPanelComponent(panel: Panel) = panel.setComponent()

  private fun Panel.setComponent(): CollapsibleRow {
    val group = collapsibleGroup(KafkaMessagesBundle.message("settings.registry.title")) {
      shortRow(registryType).layout(RowLayout.INDEPENDENT)

      confluentGroup = confluentSettings()
      glueGroup = rowsRange {
        awsGlueSettings.getComponentRows(this)
        row(glueRegistryName)
      }

      awsAccessKey.isInitialized.afterChange {
        initGlueSettings(awsGlueSettings)
      }
      awsSecretKey.isInitialized.afterChange {
        initGlueSettings(awsGlueSettings)
      }

      initGlueSettings(awsGlueSettings)
      onRegistryTypeChanged()
    }
    group.expanded = connectionData.registryType != KafkaRegistryType.NONE
    group.topGap(TopGap.NONE)
    return group
  }

  private fun Panel.confluentSettings() = rowsRange {

    row(confluentUrl).bottomGap(BottomGap.SMALL).layout(RowLayout.INDEPENDENT)
    shortRow(confluentSource).layout(RowLayout.INDEPENDENT)

    registryPropertiesGroup = block(confluentPropertiesEditor.getComponent())

    implicitRegistryClientSettingsGroup = indent {
      row(KafkaMessagesBundle.message("kafka.auth.method.label")) {
        cell(confluentSchemaAuth.getComponent())
      }.layout(RowLayout.INDEPENDENT)

      indent {
        schemaBasicAuthGroup = rowsRange {
          row {
            label(KafkaMessagesBundle.message("kafka.username")).widthGroup("A")
            confluentBasicLogin = textField().align(AlignX.FILL)
          }
          row {
            label(KafkaMessagesBundle.message("kafka.password")).widthGroup("A")
            confluentBasicPassword = passwordField().align(AlignX.FILL)
          }
        }

        schemaBearerGroup = row(KafkaMessagesBundle.message("kafka.token")) {
          confluentBearerToken = textField().align(AlignX.FILL)
        }.layout(RowLayout.INDEPENDENT)
      }

      row {
        confluentUseBrokerSsl = cell(useBrokerSslCheckbox.checkBoxField)
        confluentUseBrokerSsl.onChanged {
          updateRegistryPropertiesField()
        }
      }
      confluentSslComponent.create(this).visibleIf(confluentUseBrokerSsl.selected.not())

      row {
        confluentUseProxy = checkBox(KafkaMessagesBundle.message("kafka.registry.use.proxy"))
        confluentUseProxy.onChanged {
          updateRegistryPropertiesField()
        }
      }
      indent {
        row(KafkaMessagesBundle.message("kafka.registry.proxy.label")) {
          confluentProxyUrl = textField().align(AlignX.FILL).onChanged {
            updateRegistryPropertiesField()
          }
        }
      }.visibleIf(confluentUseProxy.selected)
    }

    updateRegistryAuthStatus()
    updateRegistryUiFromProperties()

    confluentBasicLogin.onChanged {
      updateRegistryPropertiesField()
    }
    confluentBasicPassword.onChanged {
      updateRegistryPropertiesField()
    }
    confluentBearerToken.onChanged {
      updateRegistryPropertiesField()
    }
  }

  private fun updateRegistryAuthStatus() {
    val authType = confluentSource.getValue()
    registryPropertiesGroup.visible(authType == KafkaConfigurationSource.FROM_PROPERTIES)
    implicitRegistryClientSettingsGroup.visible(authType == KafkaConfigurationSource.FROM_UI)
    updateSchemaRegistryAuth()
  }

  private fun updateSchemaRegistryAuth() {
    val selectedAuthType = confluentSchemaAuth.selectedItem
    schemaBasicAuthGroup.visible(selectedAuthType == SchemaRegistryAuthType.BASIC_AUTH)
    schemaBearerGroup.visible(selectedAuthType == SchemaRegistryAuthType.BEARER)
  }

  private fun updateRegistryUiFromProperties(): Unit = try {
    isUpdatingFromProperties.set(true)
    setRegistryProperties(confluentPropertiesEditor.getProperties() ?: emptyMap())
  }
  finally {
    isUpdatingFromProperties.set(false)
  }

  private fun updateRegistryPropertiesField() {
    if (isUpdatingFromProperties.get())
      return

    val uiProps = getRegistryProperties()
    confluentPropertiesEditor.mergeConfig(uiProps)
  }

  private fun setRegistryProperties(properties: Map<String, String>) {
    properties[SCHEMA_REGISTRY_URL_CONFIG]?.let {
      confluentUrl.getTextComponent().text = it
    }
    val isBasicAuth = properties[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] == SUPPORT_REGISTRY_BASIC_AUTH_TYPE
    val isBearerAuth = properties[SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE] == SUPPORT_REGISTRY_BEARER_AUTH_TYPE
    when {
      isBasicAuth -> {
        confluentSchemaAuth.selectedItem = SchemaRegistryAuthType.BASIC_AUTH
        val userInfo = properties[SchemaRegistryClientConfig.USER_INFO_CONFIG] ?: ""
        confluentBasicLogin.component.text = userInfo.takeWhile { it != ':' }
        confluentBasicPassword.component.text = userInfo.takeLastWhile { it != ':' }
      }
      isBearerAuth -> {
        confluentSchemaAuth.selectedItem = SchemaRegistryAuthType.BEARER
        confluentBearerToken.component.text = properties[SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG] ?: ""
      }
      else -> {
        confluentSchemaAuth.selectedItem = SchemaRegistryAuthType.NOT_SPECIFIED
      }
    }

    val keystoreLocation = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] ?: ""
    confluentSslComponent.applyConfig(KafkaSslConfig(
      validateHostName = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] != "",
      truststoreLocation = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] ?: "",
      truststorePassword = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] ?: "",
      useKeyStore = keystoreLocation.isNotBlank(),
      keyPassword = properties[SslConfigs.SSL_KEY_PASSWORD_CONFIG] ?: "",
      keystoreLocation = keystoreLocation,
      keystorePassword = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] ?: "",
      caCertificate = properties[KafkaSslUtils.SSL_CA_LOCATION] ?: "",
      accessKey = properties[KafkaSslUtils.SSL_KEY_LOCATION] ?: "",
      accessCertificate = properties[KafkaSslUtils.SSL_CERTIFICATE_LOCATION] ?: "",
    ))

    val proxyHost = properties[SchemaRegistryClientConfig.PROXY_HOST]
    val proxyPort = properties[SchemaRegistryClientConfig.PROXY_PORT]
    val isProxySetup = proxyHost != null && proxyPort != null
    confluentUseProxy.selected(isProxySetup)
    if (isProxySetup)
      confluentProxyUrl.text("$proxyHost:$proxyPort")
  }

  private fun getRegistryProperties(): Map<String, String?> {
    val default = mapOf(
      SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to null,
      SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE to null,
      SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG to null,
      SchemaRegistryClientConfig.USER_INFO_CONFIG to null,
    )

    @Suppress("DEPRECATION")
    val auth = when (confluentSchemaAuth.selectedItem) {
      SchemaRegistryAuthType.NOT_SPECIFIED -> emptyMap<String, String?>()
      SchemaRegistryAuthType.BASIC_AUTH -> {
        mapOf(
          SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to SUPPORT_REGISTRY_BASIC_AUTH_TYPE,
          SchemaRegistryClientConfig.USER_INFO_CONFIG to "${confluentBasicLogin.component.text}:${confluentBasicPassword.component.text}"
        )
      }
      SchemaRegistryAuthType.BEARER -> {
        mapOf(
          SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE to SUPPORT_REGISTRY_BEARER_AUTH_TYPE,
          SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG to confluentBearerToken.component.text
        )
      }
    }
    val ssl = if (!useBrokerSslCheckbox.checkBoxField.isSelected) {
      val config = confluentSslComponent.getConfig()
      val result = mutableMapOf<String, String?>()
      result += mapOf(
        SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to config.truststoreLocation.ifBlank { null },
        SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to config.truststorePassword.ifBlank { null })
      if (!config.validateHostName)
        result += mapOf(SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "")
      if (config.useKeyStore) {
        result += mapOf(
          SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to config.keystoreLocation.ifBlank { null },
          SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to config.keystorePassword.ifBlank { null },
          SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEY_PASSWORD_CONFIG to config.keyPassword.ifBlank { null })
      }

      result += mapOf(KafkaSslUtils.SSL_CA_LOCATION to config.caCertificate.ifBlank { null },
                      KafkaSslUtils.SSL_KEY_LOCATION to config.accessKey.ifBlank { null },
                      KafkaSslUtils.SSL_CERTIFICATE_LOCATION to config.accessCertificate.ifBlank { null })
      result
    }
    else
      mapOf(SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEY_PASSWORD_CONFIG to null)

    val proxy = if (confluentUseProxy.selected.invoke()) {
      mapOf(
        SchemaRegistryClientConfig.PROXY_HOST to confluentProxyUrl.component.text.split(":").first(),
        SchemaRegistryClientConfig.PROXY_PORT to confluentProxyUrl.component.text.split(":").last(),
      )
    }
    else {
      mapOf(
        SchemaRegistryClientConfig.PROXY_HOST to null,
        SchemaRegistryClientConfig.PROXY_PORT to null,
      )
    }
    return default + ssl + auth + proxy + mapOf(SCHEMA_REGISTRY_URL_CONFIG to confluentUrl.getTextComponent().text)
  }

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(registryType, confluentSource, confluentPropertiesEditor, confluentUrl, glueSettings, awsAccessKey, awsSecretKey,
           glueRegistryName, useBrokerSslCheckbox)

  private fun onRegistryTypeChanged() {
    when (registryType.getValue()) {
      KafkaRegistryType.NONE -> {
        confluentGroup.visible(false)
        glueGroup.visible(false)
      }
      KafkaRegistryType.CONFLUENT -> {
        confluentGroup.visible(true)
        glueGroup.visible(false)
      }
      KafkaRegistryType.AWS_GLUE -> {
        confluentGroup.visible(false)
        glueGroup.visible(true)
      }
    }
  }

  private fun initGlueSettings(settings: AwsSettingsComponentForKafka) {
    val jsonSettings = glueSettings.getTextComponent().text.ifBlank { null }
    val info = jsonSettings?.let { BdtJson.fromJsonToClass(it, StaticAwsSettingsInfo::class.java) } ?: StaticAwsSettingsInfo(
      AuthenticationType.DEFAULT.id)

    settings.loadInfo(info.copy(accessKey = awsAccessKey.getValue(), secretKey = String(awsSecretKey.getValue())))
    awsGlueSettings.updateVisibility()
    saveGlueSettings()
  }

  private fun saveGlueSettings() {
    val awsSettingsInfo = awsGlueSettings.getInfo()
    awsAccessKey.getTextComponent().text = awsSettingsInfo.accessKey
    awsSecretKey.getTextComponent().text = awsSettingsInfo.secretKey
    val newValue = BdtJson.toJson(awsSettingsInfo.copy(accessKey = null, secretKey = null))
    glueSettings.getTextComponent().text = newValue
  }

  companion object {
    private const val SUPPORT_REGISTRY_BASIC_AUTH_TYPE = "USER_INFO"
    private const val SUPPORT_REGISTRY_BEARER_AUTH_TYPE = "STATIC_TOKEN"
    private val USE_BROKER_SSL = ModificationKey(KafkaMessagesBundle.message("kafka.registry.use.broker.ssl.settings.checkbox"))
  }
}
