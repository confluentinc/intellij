package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.client.KafkaConnectionChecker
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.settings.connections.ConnectionConfigurable
import com.jetbrains.bigdatatools.settings.connections.ConnectionTesting
import com.jetbrains.bigdatatools.settings.defaultui.ConnectionError
import com.jetbrains.bigdatatools.settings.defaultui.ConnectionStatus
import com.jetbrains.bigdatatools.settings.defaultui.ConnectionSuccessful
import icons.BigdatatoolsKafkaIcons

class KafkaConnectionConfigurable(connectionData: KafkaConnectionData, project: Project) :
  ConnectionConfigurable<KafkaConnectionData>(connectionData, project, BigdatatoolsKafkaIcons.Kafka) {
  override fun createSettingsCustomizer() = KafkaSettingsCustomizer(project, connectionData, disposable)

  override fun createConnectionTesting(): ConnectionTesting<KafkaConnectionData> = object : ConnectionTesting<KafkaConnectionData> {
    override fun testConnection(conn: KafkaConnectionData,
                                testDisposable: Disposable,
                                callback: (ConnectionStatus) -> Unit) {
      val error = KafkaConnectionChecker.checkConnection(conn, testDisposable)
      if (error == null) {
        callback(ConnectionSuccessful())
      }
      else {
        callback(ConnectionError(error.cause ?: error))
      }
    }
  }
}