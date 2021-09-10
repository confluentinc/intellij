package com.jetbrains.bigdatatools.kafka

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.LightPlatformTestCase
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.rfs.driver.ConnectedConnectionStatus
import com.jetbrains.bigdatatools.rfs.driver.FailedConnectionStatus
import junit.framework.TestCase


class KafkaClientTest : LightPlatformTestCase() {
  private val url = "172.31.128.18:9092"
  private val nonExistsUrl = "172.31.128.18:9 091`"

  fun testCheckConnectionSuccess() {
    val kafkaClient = createClient(url)
    TestCase.assertEquals(ConnectedConnectionStatus, kafkaClient.connectWithStatus())
  }

  fun testCheckConnectionError() {
    val kafkaClient = createClient(nonExistsUrl)
    assert(kafkaClient.connectWithStatus() is FailedConnectionStatus)
  }

  fun testGetAllTopics() {
    val kafkaClient = createClient(url)
    TestCase.assertEquals(ConnectedConnectionStatus, kafkaClient.connectWithStatus())

    val allTopics = kafkaClient.getTopics(true)
    assert(allTopics.size > 5)
    val notInternalTopics = kafkaClient.getTopics(false)
    assertNotEmpty(notInternalTopics)
    assert(allTopics.size > notInternalTopics.size)
  }

  fun testCreateProducer() {
    val kafkaClient = createClient(url)
    TestCase.assertEquals(ConnectedConnectionStatus, kafkaClient.connectWithStatus())

    val allTopics = kafkaClient.getTopics(true)
    val testTopic = allTopics.first { it.name == "TestTopic" }
    val producerClient = kafkaClient.createProducerClient()

    val sendMessageCount = 5
    val time = System.currentTimeMillis()
    for (index in time until time + sendMessageCount) {
      val result = producerClient.sentMessage(testTopic.name, index.toString(), "Hello Mom $index")
    }
  }


  fun testGetAllConsumerGroups() {
    val kafkaClient = createClient(url)
    TestCase.assertEquals(ConnectedConnectionStatus, kafkaClient.connectWithStatus())
    val allTopics = kafkaClient.getConsumerGroups()
    assert(allTopics.size >= 3)
  }

  private fun createClient(url: String): KafkaClient {
    val conn = KafkaConnectionData().also {
      it.uri = url
    }

    return KafkaClient(null, conn, false).also {
      Disposer.register(testRootDisposable, it)
    }
  }
}