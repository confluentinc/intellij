package com.jetbrains.bigdatatools.kafka

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import com.jetbrains.bigdatatools.rfs.driver.Driver
import com.jetbrains.bigdatatools.rfs.settings.RemoteFsDriverProvider
import javax.swing.Icon

class KafkaConnectionData : RemoteFsDriverProvider(KafkaMessagesBundle.message("config.name.default")) {
  override fun getIcon(): Icon {
    TODO("Not yet implemented")
  }

  override fun createDriverImpl(project: Project?, safe: Boolean): Driver? {
    TODO("Not yet implemented")
  }
}