package io.confluent.kafka.core.rfs.driver.task

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.NlsContexts
import io.confluent.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.DriverException
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.util.KafkaMessagesBundle

abstract class RfsCopyMoveTask(protected open val fromDriver: Driver,
                               private val fromPath: RfsPath,
                               protected open val toDriver: Driver,
                               open val rootToPath: RfsPath) : RemoteFsTask(
  KafkaMessagesBundle.message("copy.file.process.text", fromPath, rootToPath)) {
  open fun isNeedPrecalculate(): Boolean = true

  @NlsContexts.DialogMessage
  open fun moveUserInfoMessage(): String? = null

  final override fun run(indicator: ProgressIndicator) {
    error("Wrong run copy task")
  }

  abstract fun run(context: RfsCopyMoveContext)


  fun failedToCopyException(additionalComment: String? = null, cause: Throwable? = null): DriverException {
    val fromPath = "${fromDriver.presentableName}/$fromPath"
    val toPath = "${toDriver.presentableName}/$rootToPath"
    val comment = additionalComment?.let { " : $it" } ?: ""

    return DriverException(KafkaMessagesBundle.message("remote.fs.task.copy.error", fromPath, toPath, comment), cause)
  }
}