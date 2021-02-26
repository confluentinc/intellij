package com.jetbrains.bigdatatools.kafka.manager

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.KafkaAdminClient
import java.util.*

class KafkaClient(connectionData: KafkaConnectionData) : Disposable {
  private val kafkaAdmin: AdminClient = KafkaAdminClient.create(getKafkaProps(connectionData))

  override fun dispose() {
    kafkaAdmin.close()
  }

  fun checkConnection(): Throwable? = try {
    kafkaAdmin.describeCluster().controller().get()
    null
  }
  catch (t: Throwable) {
    t
  }

  private fun getKafkaProps(connectionData: KafkaConnectionData): Properties {
    val props = Properties()
    props["bootstrap.servers"] = connectionData.uri
    props["connections.max.idle.ms"] = 10000
    props["default.api.timeout.ms"] = 5000
    props["request.timeout.ms"] = 5000
    props["retry.backoff.ms"] = 5000
    return props
  }
}