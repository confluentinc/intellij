package com.jetbrains.bigdatatools.kafka.core.rfs.driver.task

import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.utils.RfsCopyPasteHelpers
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalFileInfo
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.nio.file.Files


open class ReadStreamToWriteStreamFsCopyTask(fromInfo: FileInfo,
                                        toPath: RfsPath,
                                        toDriver: Driver,
                                        skipIfCopyChildIsNotSupported: Boolean = false,
                                        val exportFormat: ExportFormat? = null,
                                        val move: Boolean = false,
                                        additionalParams: Map<String, Any>) : BaseRfsCopyMoveTask(fromInfo,
                                                                                                  toPath,
                                                                                                  toDriver,
                                                                                                  skipIfCopyChildIsNotSupported,
                                                                                                  additionalParams = additionalParams) {
  override fun moveUserInfoMessage() = if (move)
    KafkaMessagesBundle.message("move.notice.message.streams")
  else
    null


  override fun copyFile(context: RfsCopyMoveContext,
                        fromInfo: FileInfo,
                        toPath: RfsPath,
                        toDriver: Driver) {
    context.startProceedFile(fromInfo.path.stringRepresentation(), toPath.stringRepresentation(), fromInfo.length)

    if (!RfsCopyPasteHelpers.resolveOverwriteFileInfo(toDriver, toPath, context))
      return

    RfsCopyPasteHelpers.copyStreams(fromInfo, toDriver, toPath, context)

    if (move && !context.isCanceled)
      fromInfo.delete()

    (toDriver as? LocalDriver)?.refreshInProject(toPath)
  }

  override fun copyDir(context: RfsCopyMoveContext,
                       fromInfo: FileInfo,
                       toPath: RfsPath,
                       toDriver: Driver) {
    if (toDriver is LocalDriver) {
      val existsFile = toDriver.getFileStatus(toPath, force = true).resultOrThrow() as? LocalFileInfo
      val ioFile = existsFile?.file
      if (ioFile != null && Files.isSymbolicLink(ioFile.toPath())) {
        Files.delete(ioFile.toPath())
      }
    }

    super.copyDir(context, fromInfo, toPath, toDriver)

    if (move && !context.isCanceled)
      fromInfo.delete()
  }
}