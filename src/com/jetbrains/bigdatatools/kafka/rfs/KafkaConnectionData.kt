package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.bigdatatools.aws.settings.AwsCompatibleConnectionData
import com.intellij.bigdatatools.aws.ui.external.StaticAwsSettingsInfo
import com.intellij.bigdatatools.kafka.BigdatatoolsKafkaIcons
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.connection.exception.BdtConfigurationException
import com.jetbrains.bigdatatools.common.connection.tunnel.model.ConnectionSshTunnelData
import com.jetbrains.bigdatatools.common.connection.tunnel.model.ConnectionSshTunnelDataLegacy
import com.jetbrains.bigdatatools.common.connection.tunnel.model.TunnelableData
import com.jetbrains.bigdatatools.common.connection.tunnel.model.migrateTunnel
import com.jetbrains.bigdatatools.common.rfs.driver.Driver
import com.jetbrains.bigdatatools.common.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.common.rfs.statistics.DriverType
import com.jetbrains.bigdatatools.common.serializer.BdtJson
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.kafka.registry.KafkaRegistryType
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")), TunnelableData {
  var properties: String = ""

  var brokerConfigurationSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_PROPERTIES
  var propertySource: KafkaPropertySource = KafkaPropertySource.DIRECT

  var registryConfSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_UI
  var propertyFilePath: String? = null

  var registryType = KafkaRegistryType.NONE
  var registryUrl: String? = null
  var registryProperties: String = ""
  var glueRegistryName: String? = null

  var version: Int? = null


  var glueSettings: String? = null


  fun loadAwsGlueSettings(): StaticAwsSettingsInfo? {
    return if (registryType == KafkaRegistryType.AWS_GLUE) {
      val settingsInfo = glueSettings?.ifBlank { null }?.let { BdtJson.fromJsonToClass(it, StaticAwsSettingsInfo::class.java) }
      settingsInfo ?: throw BdtConfigurationException(KafkaMessagesBundle.message("error.configuration.glue.is.not.setup"))
      val awsCred = getCredentials(AwsCompatibleConnectionData.SECRET_KEY_ID)
      settingsInfo.copy(accessKey = awsCred?.userName ?: "", secretKey = awsCred?.getPasswordAsString() ?: "")
    }
    else {
      null
    }

  }

  override fun getIcon(): Icon = BigdatatoolsKafkaIcons.Kafka
  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = KafkaDriver(this, project, testConnection = isTest)
  override fun rfsDriverType() = DriverType.KAFKA

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) = KafkaConnectionConfigurable(this, project)

  override var tunnel = ConnectionSshTunnelDataLegacy.DEFAULT

  override fun getTunnelData(): ConnectionSshTunnelData {
    migrateTunnel(this::uri)
    return super.getTunnelData()
  }

  override fun migrate() {
    if (version != 3) {
      version = 3
      if (registryUrl != null || registryProperties.isNotBlank()) {
        registryType = KafkaRegistryType.CONFLUENT
      }
    }
  }
}