package com.jetbrains.bigdatatools.kafka.manager

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.connection.updater.IntervalUpdateSettings
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.rfs.driver.DriverConnectionStatus
import org.apache.kafka.clients.admin.KafkaAdminClient
import java.util.*

class KafkaMonitoringDataManager(project: Project?,
                                 private val connectionData: KafkaConnectionData,
                                 settings: IntervalUpdateSettings) : MonitoringDataManager(project, settings) {
  private val kafkaAdmin = KafkaAdminClient.create(getKafkaProps(connectionData))

  override fun dispose() {
    Disposer.register(this, Disposable {
      kafkaAdmin.close()
    })
  }

  override fun connect(): DriverConnectionStatus = try {
    kafkaAdmin.describeCluster().controller().get()
    DriverConnectionStatus.CONNECTED
  }
  catch (t: Throwable) {
    DriverConnectionStatus.FAILED
  }

  override fun checkConnection(): DriverConnectionStatus = connect()

  override fun getRealUrl(): String = connectionData.uri

  companion object {
    fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
      val props = Properties()
      props["bootstrap.servers"] = connectionData.uri
      props["connections.max.idle.ms"] = 10000
      props["request.timeout.ms"] = 5000
      return props
    }

  }
}