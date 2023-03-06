package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.connection.tunnel.model.ConnectionSshTunnelData
import com.jetbrains.bigdatatools.common.connection.tunnel.model.ConnectionSshTunnelDataLegacy
import com.jetbrains.bigdatatools.common.connection.tunnel.model.TunnelableData
import com.jetbrains.bigdatatools.common.connection.tunnel.model.migrateTunnel
import com.jetbrains.bigdatatools.common.rfs.driver.Driver
import com.jetbrains.bigdatatools.common.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.common.rfs.statistics.DriverType
import com.jetbrains.bigdatatools.common.settings.connections.ConnectionGroup
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import icons.BigdatatoolsKafkaIcons
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")), TunnelableData {
  var properties: String = ""

  var brokerConfigurationSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_PROPERTIES
  var propertySource: KafkaPropertySource = KafkaPropertySource.DIRECT

  var registryConfSource: KafkaConfigurationSource = KafkaConfigurationSource.FROM_UI
  var propertyFilePath: String? = null

  var registryUrl: String? = null
  var registryProperties: String = ""

  override fun getIcon(): Icon = BigdatatoolsKafkaIcons.Kafka
  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = KafkaDriver(this, project)
  override fun rfsDriverType() = DriverType.KAFKA

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) = KafkaConnectionConfigurable(this, project)

  override var tunnel = ConnectionSshTunnelDataLegacy.DEFAULT

  override fun getTunnelData(): ConnectionSshTunnelData {
    migrateTunnel(this::uri)
    return super.getTunnelData()
  }
}