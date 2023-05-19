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
import com.jetbrains.bigdatatools.common.settings.withUrlValidator
import com.jetbrains.bigdatatools.common.ui.block
import com.jetbrains.bigdatatools.common.ui.components.RadioComboBox
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.ui.shortRow
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConfigurationSource
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.SchemaRegistryAuthType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.common.config.SslConfigs
import java.util.concurrent.atomic.AtomicBoolean

class KafkaRegistrySettings(val project: Project,
                            val connectionData: KafkaConnectionData,
                            uiDisposable: Disposable) {
  private val registryType = RadioGroupField(KafkaConnectionData::registryType,
                                             ModificationKey(KafkaMessagesBundle.message("schema.registry.type.label")), connectionData,
                                             KafkaRegistryType.values()).apply {
    addItemListener {
      updateRegistryType()
    }
  }

  private val registryPropertiesEditor = PropertiesFieldComponent.create(
    project,
    KafkaPropertiesUtils.getRegistryPropertiesDescriptions(),
    KafkaConnectionData::registryProperties,
    KafkaSettingsCustomizer.KafkaSettingsKeys.REGISTRY_PROPERTIES_KEY,
    connectionData, uiDisposable).also { editor ->
    editor.getComponent().whenFocusLost {
      updateRegistryUiFromProperties()
    }
  }

  private val registrySourceTypeChooser = RadioGroupField(KafkaConnectionData::registryConfSource,
                                                          KafkaSettingsCustomizer.KafkaSettingsKeys.REGISTRY_PROPERTIES_SOURCE_KEY,
                                                          connectionData,
                                                          KafkaConfigurationSource.values()).apply {
    addItemListener {
      updateRegistryAuthStatus()
    }
  }

  private val registryUrl = StringNonRequiredField(
    KafkaConnectionData::registryUrl,
    ModificationKey(KafkaMessagesBundle.message("settings.registry.url")), connectionData)
    .apply {
      emptyText = KafkaMessagesBundle.message("settings.registry.url.hint")
    }
    .withUrlValidator(uiDisposable, allowEmpty = true)
    .also { editor ->
      editor.getComponent().whenFocusLost {
        updateRegistryPropertiesField()
      }
    }

  private val schemaAuth = RadioComboBox(SchemaRegistryAuthType.values(), SchemaRegistryAuthType.NOT_SPECIFIED).apply {
    addItemListener {
      updateSchemaRegistryAuth()
      updateRegistryPropertiesField()
    }
  }

  private val awsAccessKey = UsernameNamedField(AwsSettingsConst.S3_ACCESS_KEY, connectionData,
                                                AwsCompatibleConnectionData.SECRET_KEY_ID)

  private val awsSecretKey = PasswordNamedField(AwsSettingsConst.S3_SECRET_KEY, connectionData,
                                                AwsCompatibleConnectionData.SECRET_KEY_ID)

  private val glueSettings = StringNonRequiredField(
    KafkaConnectionData::glueSettings,
    ModificationKey("GlueSettings"), connectionData)

  private val glueRegistryName = LoadingChooserComponent(
    KafkaConnectionData::glueRegistryName,
    ModificationKey(KafkaMessagesBundle.message("settings.glue.registry.name")),
    connectionData,
    AWSSchemaRegistryConstants.DEFAULT_REGISTRY_NAME,
    isEditable = true,
  ) {
    KafkaUIUtils.showAndGetGlueRegistry(project, awsGlueSettings.getInfo())
  }

  private val useBrokerSslCheckbox = CheckBoxField(KafkaConnectionData::registryUseBrokerSsl, USE_BROKER_SSL,
                                                   connectionData)

  private val awsGlueSettings = AwsSettingsComponentForKafka(includeRegionSetting = true) {
    saveGlueSettings()
  }

  private val sslComponent = KafkaSslSettingsComponent(project, ::updateRegistryPropertiesField)

  private lateinit var confluentGroup: RowsRange
  private lateinit var glueGroup: RowsRange

  private lateinit var registryPropertiesGroup: Row
  private lateinit var implicitRegistryClientSettingsGroup: RowsRange

  private lateinit var schemaBasicAuthGroup: RowsRange
  private lateinit var schemaBearerhGroup: Row
  private lateinit var schemaBasicLogin: Cell<JBTextField>
  private lateinit var schemaBasicPassword: Cell<JBPasswordField>
  private lateinit var schemaBearerToken: Cell<JBTextField>

  private val isUpdatingFromProperties = AtomicBoolean(false)

  fun setPanelComponent(panel: Panel) = panel.setComponent()

  private fun Panel.setComponent() {
    val group = collapsibleGroup(KafkaMessagesBundle.message("settings.registry.title")) {
      shortRow(registryType)
      confluentGroup = confluentSettings()
      glueGroup = rowsRange {
        awsGlueSettings.getComponentRows(this)
        row(glueRegistryName)
      }

      initGlueSettings(awsGlueSettings)
      updateRegistryType()
    }
    group.expanded = connectionData.registryType != KafkaRegistryType.NONE
    group.topGap(TopGap.NONE)
  }

  private fun Panel.confluentSettings() = rowsRange {
    row(registryUrl).bottomGap(BottomGap.SMALL)

    shortRow(registrySourceTypeChooser)

    registryPropertiesGroup = block(registryPropertiesEditor.getComponent())

    implicitRegistryClientSettingsGroup = indent {
      row(KafkaMessagesBundle.message("kafka.auth.method.label")) {
        cell(schemaAuth.getComponent())
      }
      indent {
        schemaBasicAuthGroup = rowsRange {
          row(KafkaMessagesBundle.message("kafka.username")) {
            schemaBasicLogin = textField().align(AlignX.FILL)
          }
          row(KafkaMessagesBundle.message("kafka.password")) {
            schemaBasicPassword = passwordField().align(AlignX.FILL)
          }
        }

        schemaBearerhGroup = row(KafkaMessagesBundle.message("kafka.token")) {
          schemaBearerToken = textField().align(AlignX.FILL)
        }
      }
      lateinit var useBrokerSsl: Cell<JBCheckBox>
      row {
        useBrokerSsl = cell(useBrokerSslCheckbox.checkBoxField)
        useBrokerSsl.onChanged {
          updateRegistryPropertiesField()
        }
      }
      sslComponent.create(this).visibleIf(useBrokerSsl.selected.not())

    }


    updateRegistryAuthStatus()
    updateRegistryUiFromProperties()

    schemaBasicLogin.onChanged {
      updateRegistryPropertiesField()
    }
    schemaBasicPassword.onChanged {
      updateRegistryPropertiesField()
    }
    schemaBearerToken.onChanged {
      updateRegistryPropertiesField()
    }
  }

  private fun updateRegistryAuthStatus() {
    val authType = registrySourceTypeChooser.getValue()
    registryPropertiesGroup.visible(authType == KafkaConfigurationSource.FROM_PROPERTIES)
    implicitRegistryClientSettingsGroup.visible(authType == KafkaConfigurationSource.FROM_UI)
    updateSchemaRegistryAuth()
  }

  private fun updateSchemaRegistryAuth() {
    val selectedAuthType = schemaAuth.selectedItem
    schemaBasicAuthGroup.visible(selectedAuthType == SchemaRegistryAuthType.BASIC_AUTH)
    schemaBearerhGroup.visible(selectedAuthType == SchemaRegistryAuthType.BEARER)
  }

  private fun updateRegistryUiFromProperties(): Unit = try {
    isUpdatingFromProperties.set(true)
    setRegistryProperties(registryPropertiesEditor.getProperties() ?: emptyMap())
  }
  finally {
    isUpdatingFromProperties.set(false)
  }

  private fun updateRegistryPropertiesField() {
    if (isUpdatingFromProperties.get())
      return

    val uiProps = getRegistryProperties()
    registryPropertiesEditor.mergeConfig(uiProps)
  }

  private fun setRegistryProperties(properties: Map<String, String>) {
    properties[SCHEMA_REGISTRY_URL_CONFIG]?.let {
      registryUrl.getTextComponent().text = it
    }
    val isBasicAuth = properties[SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE] == SUPPORT_REGISTRY_BASIC_AUTH_TYPE
    val isBearerAuth = properties[SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE] == SUPPORT_REGISTRY_BEARER_AUTH_TYPE
    when {
      isBasicAuth -> {
        schemaAuth.selectedItem = SchemaRegistryAuthType.BASIC_AUTH
        val userInfo = properties[SchemaRegistryClientConfig.USER_INFO_CONFIG] ?: ""
        schemaBasicLogin.component.text = userInfo.takeWhile { it != ':' }
        schemaBasicPassword.component.text = userInfo.takeLastWhile { it != ':' }
      }
      isBearerAuth -> {
        schemaAuth.selectedItem = SchemaRegistryAuthType.BEARER
        schemaBearerToken.component.text = properties[SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG] ?: ""
      }
      else -> {
        schemaAuth.selectedItem = SchemaRegistryAuthType.NOT_SPECIFIED
      }
    }

    val keystoreLocation = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG] ?: ""
    sslComponent.applyConfig(KafkaSslConfig(
      validateHostName = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG] != "",
      truststoreLocation = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG] ?: "",
      truststorePassword = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG] ?: "",
      useKeyStore = keystoreLocation.isNotBlank(),
      keyPassword = properties[SslConfigs.SSL_KEY_PASSWORD_CONFIG] ?: "",
      keystoreLocation = keystoreLocation,
      keystorePassword = properties[SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG] ?: ""
    )
    )
  }

  private fun getRegistryProperties(): Map<String, String?> {
    val default = mapOf(
      SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to null,
      SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE to null,
      SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG to null,
      SchemaRegistryClientConfig.USER_INFO_CONFIG to null,
    )

    @Suppress("DEPRECATION")
    val fromUi = when (schemaAuth.selectedItem) {
      SchemaRegistryAuthType.NOT_SPECIFIED -> emptyMap<String, String?>()
      SchemaRegistryAuthType.BASIC_AUTH -> {
        mapOf(
          SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to SUPPORT_REGISTRY_BASIC_AUTH_TYPE,
          SchemaRegistryClientConfig.USER_INFO_CONFIG to "${schemaBasicLogin.component.text}:${schemaBasicPassword.component.text}"
        )
      }
      SchemaRegistryAuthType.BEARER -> {
        mapOf(
          SchemaRegistryClientConfig.BEARER_AUTH_CREDENTIALS_SOURCE to SUPPORT_REGISTRY_BEARER_AUTH_TYPE,
          SchemaRegistryClientConfig.BEARER_AUTH_TOKEN_CONFIG to schemaBearerToken.component.text
        )
      }
    }
    val ssl = if (!useBrokerSslCheckbox.checkBoxField.isSelected) {
      val config = sslComponent.getConfig()
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
      result
    }
    else
      mapOf(SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to null,
            SchemaRegistryClientConfig.CLIENT_NAMESPACE + SslConfigs.SSL_KEY_PASSWORD_CONFIG to null)

    return default + ssl + fromUi + mapOf(SCHEMA_REGISTRY_URL_CONFIG to registryUrl.getTextComponent().text)
  }

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(registryType, registrySourceTypeChooser, registryPropertiesEditor, registryUrl, glueSettings, awsAccessKey, awsSecretKey,
           glueRegistryName, useBrokerSslCheckbox)

  private fun updateRegistryType() {
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

    settings.loadInfo(info.copy(accessKey = awsAccessKey.getComponent().text, secretKey = awsSecretKey.getComponent().text))
    awsGlueSettings.updateVisibility()
  }

  private fun saveGlueSettings() {
    val awsSettingsInfo = awsGlueSettings.getInfo()
    awsAccessKey.getComponent().text = awsSettingsInfo.accessKey
    awsSecretKey.getComponent().text = awsSettingsInfo.secretKey
    val newValue = BdtJson.toJson(awsSettingsInfo.copy(accessKey = null, secretKey = null))
    glueSettings.getTextComponent().text = newValue
  }

  companion object {
    private const val SUPPORT_REGISTRY_BASIC_AUTH_TYPE = "USER_INFO"
    private const val SUPPORT_REGISTRY_BEARER_AUTH_TYPE = "STATIC_TOKEN"
    private val USE_BROKER_SSL = ModificationKey(KafkaMessagesBundle.message("kafka.registry.use.broker.ssl.settings.checkbox"))
  }
}