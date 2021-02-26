package com.jetbrains.bigdatatools.kafka

import com.intellij.testFramework.UsefulTestCase
import com.jetbrains.bigdatatools.kafka.manager.KafkaClient
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import junit.framework.TestCase


class KafkaClientTest : UsefulTestCase() {
  private val url = "127.0.0.1:9092"
  private val nonExistsUrl = "127.0.0.2:9091"

  fun testCheckConnectionSuccess() {
    val conn = KafkaConnectionData().also {
      it.uri = url
    }
    val kafkaClient = KafkaClient(conn)
    val connectionResult = kafkaClient.checkConnection()
    TestCase.assertNull(connectionResult)
  }

  fun testCheckConnectionError() {
    val conn = KafkaConnectionData().also {
      it.uri = nonExistsUrl
    }
    val kafkaClient = KafkaClient(conn)
    val connectionResult = kafkaClient.checkConnection()
    TestCase.assertNotNull(connectionResult)
  }
}