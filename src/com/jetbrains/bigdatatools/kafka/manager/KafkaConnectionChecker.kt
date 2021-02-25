package com.jetbrains.bigdatatools.kafka.manager

import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import org.apache.kafka.clients.admin.KafkaAdminClient

object KafkaConnectionChecker {
  fun checkConnection(connectionData: KafkaConnectionData): Throwable? {
    val adminClient = KafkaAdminClient.create(KafkaMonitoringDataManager.getKafkaProps(connectionData))

    return try {
      adminClient.describeCluster().controller()
      null
    }
    catch (t: Throwable) {
      return t
    }
    finally {
      adminClient.close()
    }
  }
}