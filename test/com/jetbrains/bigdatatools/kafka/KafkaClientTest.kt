package com.jetbrains.bigdatatools.kafka

import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.bigdatatools.kafka.client.KafkaClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import junit.framework.TestCase


class KafkaClientTest : UsefulTestCase() {
  private val url = "127.0.0.1:9092"
  private val nonExistsUrl = "127.0.0.2:9091"

  fun testCheckConnectionSuccess() {
    val kafkaClient = createClient(url)
    val connectionResult = kafkaClient.checkConnection()
    TestCase.assertNull(connectionResult)
  }

  fun testCheckConnectionError() {
    val kafkaClient = createClient(nonExistsUrl)
    val connectionResult = kafkaClient.checkConnection()
    TestCase.assertNotNull(connectionResult)
  }

  fun testGetAllTopics() {
    val kafkaClient = createClient(url)
    val allTopics = kafkaClient.getTopics(true)
    assert(allTopics.size > 5)
    val notInternalTopics = kafkaClient.getTopics(false)
    assertNotEmpty(notInternalTopics)
    assert(allTopics.size > notInternalTopics.size)
  }

  fun testGetAllConsumerGroups() {
    val kafkaClient = createClient(url)
    val allTopics = kafkaClient.getConsumerGroups()
    assert(allTopics.size >= 3)
  }

  private fun createClient(url: String): KafkaClient {
    val conn = KafkaConnectionData().also {
      it.uri = url
    }

    return KafkaClient(null, conn).also {
      Disposer.register(testRootDisposable, it)
    }
  }
}