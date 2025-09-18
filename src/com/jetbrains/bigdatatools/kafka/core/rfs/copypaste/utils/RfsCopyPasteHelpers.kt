package com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.utils

import com.intellij.refactoring.RefactoringBundle
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.dialog.RfsSkipOverwriteChoice
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import java.io.InputStream
import java.io.OutputStream

object RfsCopyPasteHelpers {
  private const val CHUNK_SIZE = 1024 * 1024

  fun resolveOverwriteFileInfo(toDriver: Driver,
                               toPath: RfsPath,
                               context: RfsCopyMoveContext) = resolveOverwrite(toPath.stringRepresentation(), context) {
    toDriver.getFileStatus(toPath, force = true).resultOrThrow() != null
  }


  fun resolveOverwriteBucketable(toBucket: String,
                                 toKey: String,
                                 context: RfsCopyMoveContext,
                                 checkIsExists: () -> Boolean) = resolveOverwrite("$toBucket/$toKey", context) {
    checkIsExists()
  }

  fun resolveOverwrite(toPath: String,
                       context: RfsCopyMoveContext,
                       checkIsExists: () -> Boolean): Boolean {
    if (context.skipOverwriteChoice == RfsSkipOverwriteChoice.OVERWRITE_ALL)
      return true

    val isExists = checkIsExists()
    if (!isExists)
      return true

    if (context.skipOverwriteChoice == RfsSkipOverwriteChoice.SKIP_ALL)
      return false

    val choice = RfsSkipOverwriteChoice.askUser(context.project, toPath, toPath.split("/").last(),
                                                RefactoringBundle.message("copy.handler.copy.files.directories"), true)

    context.skipOverwriteChoice = choice
    return choice in listOf(RfsSkipOverwriteChoice.OVERWRITE, RfsSkipOverwriteChoice.OVERWRITE_ALL)
  }

  fun copyStreams(fromInfo: FileInfo,
                  toDriver: Driver,
                  toPath: RfsPath,
                  context: RfsCopyMoveContext,
                  exportFormat: ExportFormat? = null) {
    val fromStream = fromInfo.readStream(0, exportFormat).resultOrThrowWithWrapper(
      KafkaMessagesBundle.message("copy.through.local.file.failed.create.read.stream", fromInfo.externalPath))

    val toStream = toDriver.getWriteStream(toPath, overwrite = true, canCreateNewFile = true).resultOrThrowWithWrapper(
      KafkaMessagesBundle.message("copy.through.local.file.failed.create.write.stream", toPath, toDriver.presentableName))

    val freshInfo = fromInfo.driver.getFileStatus(fromInfo.path, force = true).resultOrThrow() ?: error(
      KafkaMessagesBundle.message("do.not.exists", fromInfo.path))

    copyStreams(fromStream, toStream, context, freshInfo.length)
  }


  fun copyStreams(fromStream: InputStream,
                  toStream: OutputStream,
                  context: RfsCopyMoveContext,
                  length: Long) {
    var copiedAlready = 0L
    fromStream.use { input ->
      toStream.use { output ->
        while (true) {
          if (context.isCanceled)
            break

          if (copiedAlready >= length)
            return
          val buffer = ByteArray(CHUNK_SIZE)
          val bytesRead = input.read(buffer)

          if (bytesRead <= 0)
            return
          output.write(buffer, 0, bytesRead)
          context.addFileBytes(bytesRead.toLong())
          copiedAlready += bytesRead
        }
      }
    }
  }
}