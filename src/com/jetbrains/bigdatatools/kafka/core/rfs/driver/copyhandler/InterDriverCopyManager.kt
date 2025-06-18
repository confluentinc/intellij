package com.jetbrains.bigdatatools.kafka.core.rfs.driver.copyhandler

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.task.RfsCopyMoveTask
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle

object InterDriverCopyManager {
  fun buildCopyTask(fromInfo: FileInfo,
                    toPath: RfsPath,
                    toDriver: Driver,
                    exportFormat: ExportFormat?,
                    additionalParams: Map<String, Any> = emptyMap()): RfsCopyMoveTask {
    val handler = getCopyHandler(fromInfo, toDriver)
    return handler.buildCopyTask(fromInfo, toPath, toDriver, exportFormat, additionalParams)
  }

  fun getCopyHandler(fromInfo: FileInfo,
                     toDriver: Driver): InterDriverCopyHandler {
    var compatibleHandlers = InterDriverCopyHandler.getAllSuitable(fromInfo, toDriver)


    if (compatibleHandlers.isEmpty()) {
      val streamHandler = getStreamHandler(fromInfo, toDriver)
      if (streamHandler != null)
        compatibleHandlers = listOf(streamHandler)
      else
        error(KafkaMessagesBundle.message(
          "copy.error.handler.is.not.found",
          fromInfo.driver.presentableName,
          toDriver.presentableName))
    }
    return compatibleHandlers.first()
  }

  fun canCopyToFolder(sourceFileInfos: Iterable<FileInfo>, targetPath: RfsPath, targetDriver: Driver, overwrite: Boolean): Boolean {
    if (!targetPath.isDirectory)
      return false
    val isAnyContainsYet = sourceFileInfos.all { it.driver == targetDriver } && sourceFileInfos.any { targetPath.startsWith(it.path) }
    if (isAnyContainsYet)
      return false
    val names = sourceFileInfos.map { it.name }
    val isNamesUniques = names.distinct().size == names.size
    if (!isNamesUniques)
      return false
    return canCopyToDriver(sourceFileInfos, targetDriver, overwrite)
  }

  private fun canCopyToDriver(sourceInfos: Iterable<FileInfo>, targetDriver: Driver, overwrite: Boolean): Boolean {
    if (!sourceInfos.any())
      return false

    return sourceInfos.all { fromInfo ->
      InterDriverCopyHandler.hasSuitable(fromInfo, targetDriver) ||
      hasStreamHandler(fromInfo, targetDriver)
    }
  }


  private fun getStreamHandler(fromInfo: FileInfo,
                               toDriver: Driver): HostPassThroughCopyHandler? {
    val hostPassHandler = HostPassThroughCopyHandler()
    return if (hostPassHandler.canHandle(fromInfo, toDriver))
      hostPassHandler
    else
      null
  }

  private fun hasStreamHandler(fromInfo: FileInfo,
                               toDriver: Driver): Boolean {
    val hostPassHandler = HostPassThroughCopyHandler()
    return hostPassHandler.canHandle(fromInfo, toDriver)
  }
}