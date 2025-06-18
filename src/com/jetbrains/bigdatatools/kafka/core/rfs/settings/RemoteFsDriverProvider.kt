package com.jetbrains.bigdatatools.kafka.core.rfs.settings

import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.constants.BdtConnectionType
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.BrokenDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.kafka.core.settings.connections.ConnectionData
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.Icon

abstract class RemoteFsDriverProvider(name: String = "") : ConnectionData(name = name) {
  final override fun createDriver(project: Project?, isTest: Boolean): Driver {
    val driver = try {
      createDriverImpl(if (isPerProject) project else null, isTest)
    }
    catch (e: Exception) {
      if (isTest)
        throw e
      RfsNotificationUtils.notifyException(e, KafkaMessagesBundle.message("error.while.creating.connection"))
      BrokenDriver(project, name, innerId, getIcon(), this, e)
    }

    return driver
  }

  abstract fun getIcon(): Icon

  protected abstract fun createDriverImpl(project: Project?, isTest: Boolean): Driver

  abstract fun rfsDriverType(): BdtConnectionType
}