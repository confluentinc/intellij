package io.confluent.intellijplugin.core.rfs.driver.copyhandler

import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.ExportFormat
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.task.ReadStreamToWriteStreamFsCopyTask
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class HostPassThroughCopyHandler : InterDriverCopyHandler {
  override fun canHandle(fromInfo: FileInfo, toDriver: Driver): Boolean {
    val fromDriver = fromInfo.driver
    return fromDriver.isAvailableCopyThroughIoStreams && toDriver.isAvailableCopyThroughIoStreams
  }

  override fun buildCopyTask(fromInfo: FileInfo,
                             toPath: RfsPath,
                             toDriver: Driver,
                             exportFormat: ExportFormat?,
                             additionalParams: Map<String, Any>) =
    ReadStreamToWriteStreamFsCopyTask(fromInfo, toPath, toDriver, exportFormat = exportFormat, additionalParams = additionalParams)

  override fun userInfoMessage() = KafkaMessagesBundle.message("copy.notice.message.streams")
}