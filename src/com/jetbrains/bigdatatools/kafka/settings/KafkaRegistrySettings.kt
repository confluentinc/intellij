package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenFocusLost
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.jetbrains.bigdatatools.common.connection.tunnel.ui.SshTunnelComponent
import com.jetbrains.bigdatatools.common.constants.BdtConnectionType
import com.jetbrains.bigdatatools.common.settings.ModificationKey
import com.jetbrains.bigdatatools.common.settings.fields.*
import com.jetbrains.bigdatatools.common.settings.manager.RfsConnectionDataManager
import com.jetbrains.bigdatatools.common.settings.withUrlValidator
import com.jetbrains.bigdatatools.common.ui.block
import com.jetbrains.bigdatatools.common.ui.components.RadioComboBox
import com.jetbrains.bigdatatools.common.ui.row
import com.jetbrains.bigdatatools.common.ui.shortRow
import com.jetbrains.bigdatatools.common.util.BdtUrlUtils
import com.jetbrains.bigdatatools.common.util.MessagesBundle
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConfigurationSource
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.rfs.SchemaRegistryAuthType
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.kafka.util.KafkaPropertiesUtils
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import java.util.concurrent.atomic.AtomicBoolean

class KafkaRegistrySettings(val project: Project,
                            val connectionData: KafkaConnectionData,
                            uiDisposable: Disposable,
                            private val tunnelField: SshTunnelComponent<KafkaConnectionData>) {
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

  private val glueConnectionComboBox = ComboBoxField(KafkaConnectionData::glueConnectionId,
                                                     ModificationKey(KafkaMessagesBundle.message("settings.glue.driver.id")),
                                                     connectionData,
                                                     getGlueDriversIds(),
                                                     ::getSparkMonitoringConnectionNames)


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
    group(KafkaMessagesBundle.message("settings.registry.title")) {
      shortRow(registryType)
      confluentGroup = confluentSettings()
      glueGroup = rowsRange {
        row {
          label(KafkaMessagesBundle.message("settings.glue.driver.id"))
          cell(glueConnectionComboBox.getComponent())
        }
      }
      updateRegistryType()
    }
  }

  private fun Panel.confluentSettings() = rowsRange {
    row(registryUrl).bottomGap(BottomGap.SMALL)

    shortRow(registrySourceTypeChooser)

    registryPropertiesGroup = block(registryPropertiesEditor.getComponent())

    implicitRegistryClientSettingsGroup = rowsRange {
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
    }
    block(tunnelField.getComponent()).topGap(TopGap.SMALL)


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
    return default + fromUi + mapOf(SCHEMA_REGISTRY_URL_CONFIG to registryUrl.getTextComponent().text)
  }

  fun getDefaultFields(): List<WrappedComponent<in KafkaConnectionData>> =
    listOf(registryType, registrySourceTypeChooser, registryPropertiesEditor, registryUrl, glueConnectionComboBox)

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

  private fun getGlueDriversIds(): Array<String> {
    val connections = RfsConnectionDataManager.instance?.getConnectionsByGroupId(BdtConnectionType.GLUE.id, project) ?: emptyList()
    return if (connections.isEmpty()) arrayOf("") else arrayOf("") + connections.map { it.innerId }.toTypedArray()
  }

  private fun getSparkMonitoringConnectionNames(innerId: String): String? {
    return RfsConnectionDataManager.instance?.getConnectionById(project, innerId)?.name
  }


  companion object {
    private const val SUPPORT_REGISTRY_BASIC_AUTH_TYPE = "USER_INFO"
    private const val SUPPORT_REGISTRY_BEARER_AUTH_TYPE = "STATIC_TOKEN"
  }
}