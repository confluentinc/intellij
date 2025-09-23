package io.confluent.kafka.core.rfs.settings

import com.intellij.openapi.project.Project
import io.confluent.kafka.core.constants.BdtConnectionType
import io.confluent.kafka.core.rfs.driver.BrokenDriver
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.util.RfsNotificationUtils
import io.confluent.kafka.core.settings.connections.ConnectionData
import io.confluent.kafka.util.KafkaMessagesBundle
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