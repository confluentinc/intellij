package com.jetbrains.bigdatatools.kafka.rfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.settings.KafkaConnectionConfigurable
import com.jetbrains.bigdatatools.kafka.util.KafkaIcons
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.rfs.driver.Driver
import com.jetbrains.bigdatatools.rfs.settings.RemoteFsDriverProvider
import com.jetbrains.bigdatatools.rfs.util.DriverUtil.withInit
import com.jetbrains.bigdatatools.settings.connections.ConnectionGroup
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")) {
  override fun getIcon(): Icon = KafkaIcons.MAIN_ICON
  override fun createDriverImpl(project: Project?, safe: Boolean): Driver = withInit(KafkaDriver(this, project))
  override fun rfsDriverType() = TYPE_ID

  override fun createConfigurable(project: Project,
                                  parentGroup: ConnectionGroup,
                                  uiDisposable: Disposable) = KafkaConnectionConfigurable(this, project, uiDisposable)

  companion object {
    const val TYPE_ID = "Kafka"
  }
}