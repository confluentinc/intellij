package com.jetbrains.bigdatatools.kafka

import com.intellij.testFramework.UsefulTestCase
import org.apache.kafka.clients.admin.KafkaAdminClient
import java.util.*
import java.util.concurrent.TimeUnit


class KafkaManagerTest : UsefulTestCase() {
  fun testAdmin() {
    val props = getKafkaProps()
    val admin = KafkaAdminClient.create(props)
    val topicNames = admin.listTopics().names().get(10, TimeUnit.SECONDS)
    val describedTopics = admin.describeTopics(topicNames).all().get(10, TimeUnit.SECONDS)

    val nodes = admin.describeCluster().nodes().get(10, TimeUnit.SECONDS)
    val id = admin.describeCluster().clusterId().get(10, TimeUnit.SECONDS)
    val cntroller = admin.describeCluster().controller().get(10, TimeUnit.SECONDS)
    val metrics = admin.metrics()
    metrics.entries.forEach {
      val metricValue = it.value.metricValue()
      val name = it.value.metricName()
      val z = 0
    }

    val z = 0
  }

  private fun getKafkaProps(): Properties {
    val props = Properties()
    props.put("bootstrap.servers", "127.0.0.1:9092")
    return props
  }
}