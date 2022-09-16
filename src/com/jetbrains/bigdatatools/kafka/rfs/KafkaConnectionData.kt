package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelConnectionUtils
import com.jetbrains.bigdatatools.connection.tunnel.model.*
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.rfs.driver.Driver
import com.jetbrains.bigdatatools.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.rfs.statistics.DriverType
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import icons.BigdatatoolsKafkaIcons
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")), TunnelableData {
  var properties: String = ""
  var propertySource: KafkaPropertySource = KafkaPropertySource.DIRECT
  var propertyFilePath: String? = null

  override fun getIcon(): Icon = BigdatatoolsKafkaIcons.Kafka
  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = KafkaDriver(this, project)
  override fun rfsDriverType() = DriverType.KAFKA

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) = KafkaConnectionConfigurable(this, project)

  override var tunnel = ConnectionSshTunnelDataLegacy.DEFAULT

  override fun getTunnelData(): ConnectionSshTunnelData {
    migrateTunnel(this::uri)
    return super.getTunnelData()
  }

  override fun getTunnelInfo(): ConnectionSshTunnelInfo? {
    val tunnelData = getTunnelData()
    return BdtSshTunnelConnectionUtils.getTunnelInfo(uri, tunnelData)
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)

    const val TYPE_ID = "Kafka"
  }
}