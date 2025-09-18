package com.jetbrains.bigdatatools.kafka.core.rfs.driver.copyhandler

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.task.ReadStreamToWriteStreamFsCopyTask
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

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