package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.manager.KafkaConnectionChecker
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.settings.connections.ConnectionConfigurable
import com.jetbrains.bigdatatools.settings.connections.ConnectionTesting
import com.jetbrains.bigdatatools.settings.defaultui.ConnectionError
import com.jetbrains.bigdatatools.settings.defaultui.ConnectionSuccessful
import com.jetbrains.bigdatatools.util.toPresentableText

class KafkaConnectionConfigurable(connectionData: KafkaConnectionData, project: Project, uiDisposable: Disposable) :
  ConnectionConfigurable<KafkaConnectionData>(connectionData, project, uiDisposable, KafkaIcons.MAIN_ICON) {

  override fun createConnectionTesting(): ConnectionTesting<KafkaConnectionData> = object : ConnectionTesting<KafkaConnectionData> {
    override fun testConnection(conn: KafkaConnectionData,
                                callback: (com.jetbrains.bigdatatools.settings.defaultui.ConnectionStatus) -> Unit) {
      val error = KafkaConnectionChecker.checkConnection(conn)
      if (error == null) {
        callback(ConnectionSuccessful(null, KafkaMessagesBundle.message("connection.success")))
      }
      else {
        callback(ConnectionError(error.toPresentableText(), "Connection error"))
      }
    }
  }
}