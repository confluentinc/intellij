package io.confluent.kafka.rfs

import com.amazonaws.services.schemaregistry.utils.AWSSchemaRegistryConstants
import io.confluent.kafka.icons.BigdatatoolsKafkaIcons
import com.intellij.credentialStore.Credentials
import com.intellij.openapi.project.Project
import io.confluent.kafka.aws.settings.AwsCompatibleConnectionData.Companion.SECRET_KEY_ID
import io.confluent.kafka.aws.ui.external.StaticAwsSettingsInfo
import io.confluent.kafka.core.connection.exception.BdtConfigurationException
import io.confluent.kafka.core.connection.tunnel.model.ConnectionSshTunnelData
import io.confluent.kafka.core.connection.tunnel.model.ConnectionSshTunnelDataLegacy
import io.confluent.kafka.core.connection.tunnel.model.TunnelableData
import io.confluent.kafka.core.connection.tunnel.model.migrateTunnel
import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.settings.RemoteFsDriverProvider
import io.confluent.kafka.core.serializer.BdtJson
import io.confluent.kafka.core.settings.DoNotSerialize
import io.confluent.kafka.core.settings.connections.ConnectionGroup
import io.confluent.kafka.core.settings.connections.CredentialId
import io.confluent.kafka.registry.KafkaRegistryType
import io.confluent.kafka.settings.KafkaConnectionConfigurable
import io.confluent.kafka.util.KafkaMessagesBundle
import javax.swing.Icon

class KafkaConnectionData(var version: Int? = null) : RemoteFsDriverProvider(
  KafkaMessagesBundle.message("config.name.default")), TunnelableData {
  @Deprecated("Start use secret config")
  var properties: String = ""

  @DoNotSerialize
  var secretProperties: String
    get() = getCredentials(CONFIG_KEY)?.userName ?: ""
    set(value) {
      setCredentials(Credentials(value, null as? String?), CONFIG_KEY)
    }

  override fun credentialIds() = super.credentialIds() + CONFIG_KEY + CONFIG_REGISTRY_KEY + SECRET_KEY_ID

  var brokerConfigurationSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_UI
  var brokerCloudSource: KafkaCloudType = KafkaCloudType.CONFLUENT

  var propertySource: KafkaPropertySource = KafkaPropertySource.DIRECT

  var registryConfSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_UI
  var propertyFilePath: String? = null

  var registryType = KafkaRegistryType.NONE
  var registryUrl: String? = null

  @Deprecated("Start use secret config")
  var registryProperties: String = ""

  @DoNotSerialize
  var secretRegistryProperties: String
    get() = getCredentials(CONFIG_REGISTRY_KEY)?.userName ?: ""
    set(value) {
      setCredentials(Credentials(value, null as? String?), CONFIG_REGISTRY_KEY)
    }

  var registryUseBrokerSsl: Boolean = true
  var glueRegistryName: String? = null


  var glueSettings: String? = null


  fun loadAwsGlueSettings(): StaticAwsSettingsInfo? {
    return if (registryType == KafkaRegistryType.AWS_GLUE) {
      val settingsInfo = glueSettings?.ifBlank { null }?.let { BdtJson.fromJsonToClass(it, StaticAwsSettingsInfo::class.java) }
      settingsInfo ?: throw BdtConfigurationException(KafkaMessagesBundle.message("error.configuration.glue.is.not.setup"))
      val awsCred = getCredentials(SECRET_KEY_ID)
      settingsInfo.copy(accessKey = awsCred?.userName ?: "", secretKey = awsCred?.getPasswordAsString() ?: "")
    }
    else {
      null
    }
  }

  fun getGlueRegistryOrDefault() = glueRegistryName ?: AWSSchemaRegistryConstants.DEFAULT_REGISTRY_NAME

  override fun getIcon(): Icon = BigdatatoolsKafkaIcons.Kafka
  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = KafkaDriver(this, project, testConnection = isTest)
  override fun rfsDriverType() = BdtConnectionType.KAFKA

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) = KafkaConnectionConfigurable(this, project)

  override var tunnel: ConnectionSshTunnelDataLegacy = ConnectionSshTunnelDataLegacy.DEFAULT

  override fun getTunnelData(): ConnectionSshTunnelData {
    migrateTunnel(this::uri)
    return super.getTunnelData()
  }

  override fun migrate() {
    if (version == null || version!! < 3) {
      version = 3
      @Suppress("DEPRECATION")
      if (registryUrl != null || registryProperties.isNotBlank()) {
        registryType = KafkaRegistryType.CONFLUENT
      }
    }

    @Suppress("DEPRECATION")
    if (version!! < 4) {
      version = 4

      secretProperties = properties
      properties = ""
    }

    @Suppress("DEPRECATION")
    if (version!! < 5) {
      version = 5

      secretRegistryProperties = registryProperties
      registryProperties = ""
    }

  }

  companion object {
    val CONFIG_KEY = CredentialId("broker.secret.properties")
    val CONFIG_REGISTRY_KEY = CredentialId("registry.secret.properties")
  }
}