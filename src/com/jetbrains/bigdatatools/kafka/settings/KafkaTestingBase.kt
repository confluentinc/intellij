package com.jetbrains.bigdatatools.kafka.settings

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.common.rfs.driver.Driver
import com.jetbrains.bigdatatools.common.rfs.settings.RfsConnectionTestingBase
import com.jetbrains.bigdatatools.common.settings.defaultui.ConnectionError
import com.jetbrains.bigdatatools.common.settings.defaultui.ConnectionStatus
import com.jetbrains.bigdatatools.common.settings.defaultui.ConnectionSuccessful
import com.jetbrains.bigdatatools.common.settings.defaultui.SettingsPanelCustomizer
import com.jetbrains.bigdatatools.kafka.client.KafkaConnectionChecker
import com.jetbrains.bigdatatools.kafka.rfs.KafkaConnectionData

class KafkaTestingBase(project: Project,
                       settingsCustomizer: SettingsPanelCustomizer<*>?) : RfsConnectionTestingBase<KafkaConnectionData>(project,
                                                                                                                        settingsCustomizer) {
  override fun checkConnection(conn: KafkaConnectionData,
                               extendedCallback: (Driver?, ConnectionStatus) -> Unit,
                               testDisposable: Disposable) {
    val error = KafkaConnectionChecker.checkConnection(conn, testDisposable)
    if (error == null) {
      extendedCallback(null, ConnectionSuccessful())
    }
    else {
      extendedCallback(null, ConnectionError(error.cause ?: error))
    }
  }
}