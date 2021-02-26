package com.jetbrains.bigdatatools.kafka.manager

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

object KafkaConnectionChecker {
  fun checkConnection(connectionData: KafkaConnectionData, testDisposable: Disposable): Throwable? {
    val client = KafkaClient(connectionData)
    Disposer.register(testDisposable, client)
    return client.checkConnection()
  }
}