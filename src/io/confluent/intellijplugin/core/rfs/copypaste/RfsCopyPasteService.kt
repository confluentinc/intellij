package io.confluent.intellijplugin.core.rfs.copypaste

import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.driver.copyhandler.InterDriverCopyManager
import io.confluent.intellijplugin.core.rfs.driver.task.RfsCopyMoveTask
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import org.jetbrains.annotations.Nls

object RfsCopyPasteService {
  fun getUserConfirmMessageForFolder(sourceFile: FileInfo,
                                     toDriver: Driver): @Nls String? {

    return InterDriverCopyManager.getCopyHandler(sourceFile, toDriver).userInfoMessage()
  }

  fun createCopyTaskToFolder(sourceFile: FileInfo,
                             toPath: RfsPath,
                             toDriver: Driver,
                             exportFormat: ExportFormat?,
                             newName: String? = null,
                             additionalParams: Map<String, Any> = emptyMap()
  ): RfsCopyMoveTask {
    val targetPath = if (newName != null)
      toPath.child(newName, sourceFile.isDirectory)
    else
      RfsCopyPasteUtil.getCorrectTargetPath(sourceFile, toPath, toDriver, exportFormat)

    return createCopyTask(sourceFile, targetPath, toDriver, exportFormat, additionalParams = additionalParams)
  }

  private fun createCopyTask(sourceFile: FileInfo,
                             toPath: RfsPath,
                             toDriver: Driver,
                             exportFormat: ExportFormat?,
                             additionalParams: Map<String, Any> = emptyMap()): RfsCopyMoveTask {
    val sourceRfsPath = sourceFile.path

    if (sourceRfsPath.isFile != toPath.isFile)
      throw DriverException(KafkaMessagesBundle.message("copy.file.as.dir", sourceRfsPath, toPath))

    return InterDriverCopyManager.buildCopyTask(sourceFile, toPath, toDriver, exportFormat, additionalParams)
  }
}