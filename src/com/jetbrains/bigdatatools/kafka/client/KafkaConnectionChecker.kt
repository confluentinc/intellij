package com.jetbrains.bigdatatools.kafka.client

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

object KafkaConnectionChecker {
  fun checkConnection(connectionData: KafkaConnectionData, testDisposable: Disposable): Throwable? {
    val client = try {
      KafkaClient(null, connectionData, testConnection = true)
    }
    catch (t: Throwable) {
      return t
    }
    Disposer.register(testDisposable, client)

    return client.connectWithThrowable()
  }
}