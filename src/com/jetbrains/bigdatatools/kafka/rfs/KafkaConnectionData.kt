package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.connection.tunnel.BdtSshTunnelConnectionUtils
import com.jetbrains.bigdatatools.connection.tunnel.model.ConnectionSshTunnelInfo
import com.jetbrains.bigdatatools.connection.tunnel.model.TunnelableData
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.rfs.driver.Driver
import com.jetbrains.bigdatatools.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")), TunnelableData {
  var properties: String = ""
  var propertySource: KafkaPropertySource = KafkaPropertySource.DIRECT
  var propertyFilePath: String? = null

  override fun getIcon(): Icon = KafkaIcons.MAIN_ICON
  override fun createDriverImpl(project: Project?, isTest: Boolean): Driver = KafkaDriver(this, project)
  override fun rfsDriverType() = TYPE_ID

  override fun createConfigurable(project: Project, parentGroup: ConnectionGroup) = KafkaConnectionConfigurable(this, project)

  override var tunnel = ConnectionSshTunnelInfo.DEFAULT

  override fun getTunnelData(): ConnectionSshTunnelInfo {
    try {
      val (newUri, newTunnel) = BdtSshTunnelConnectionUtils.transformToConfigVersion2(uri, tunnel)
      uri = newUri
      tunnel = newTunnel
    }
    catch (t: Throwable) {
      logger.warn(t)
    }

    return BdtSshTunnelConnectionUtils.getData(uri, tunnel)
  }

  override fun setTunnelData(info: ConnectionSshTunnelInfo) {
    tunnel = info
  }

  companion object {
    private val logger = Logger.getInstance(this::class.java)

    const val TYPE_ID = "Kafka"
  }
}