package com.jetbrains.bigdatatools.kafka.core.rfs.copypaste

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.CopyOrMove
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.TargetInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ExportFormat
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.copyhandler.InterDriverCopyHandler
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.local.LocalDriverManager
import com.jetbrains.bigdatatools.kafka.core.rfs.util.RfsNotificationUtils
import com.jetbrains.bigdatatools.kafka.core.util.invokeAndWaitSwing
import java.io.File

object RfsCopyPasteUtil {
  fun getCorrectTargetPath(fromInfo: FileInfo,
                           toParentPath: RfsPath,
                           toDriver: Driver,
                           exportFormat: ExportFormat?): RfsPath {
    val pathFromHandlers = InterDriverCopyHandler.getCorrectTargetPath(fromInfo, toParentPath, toDriver,
                                                                       exportFormat)
    if (pathFromHandlers != null)
      return pathFromHandlers

    val name = fromInfo.nameForDriver(toDriver, exportFormat).removeSuffix("/")
    return toParentPath.child(name, fromInfo.isDirectory)
  }


  fun copyMove(project: Project,
               targetInfo: TargetInfo,
               sourceFileInfos: List<FileInfo>,
               move: Boolean,
               additionalParams: Map<String, Any> = emptyMap(),
               onFileLoad: suspend (List<RfsPath>) -> Unit = {},
               runInBackground: Boolean): RfsCopyMoveContext {
    val copyMoveTask = CopyMoveTask(project = project,
                                    targetDriver = targetInfo.targetDriver,
                                    targetFolderPath = targetInfo.targetFolder,
                                    overrideTargetName = targetInfo.targetName,
                                    exportFormat = targetInfo.exportFormat,
                                    sourceFileInfos = sourceFileInfos,
                                    additionalParams = additionalParams,
                                    move = move,
                                    onFileLoad = onFileLoad)
    executeTask(copyMoveTask, runInBackground)
    return copyMoveTask.copyMoveContext
  }


  @Suppress("DialogTitleCapitalization")
  private fun executeTask(copyMoveTask: CopyMoveTask, runInBackground: Boolean) {
    if (runInBackground) {
      ProgressManager.getInstance().run(copyMoveTask)
    }
    else {
      val modalTask = object : Task.Modal(copyMoveTask.project, copyMoveTask.title, copyMoveTask.isCancellable) {
        override fun run(indicator: ProgressIndicator) {
          copyMoveTask.run(indicator)
        }

        override fun onCancel() {
          copyMoveTask.onCancel()
          super.onCancel()
        }
      }
      ProgressManager.getInstance().run(modalTask)
      copyMoveTask.copyMoveContext.finishResult?.onFailure {
        invokeAndWaitSwing {
          RfsNotificationUtils.showExceptionMessage(project = null, it)
        }
      }
    }
  }

  internal fun getFileInfoFromVirtualFiles(uploadingFiles: List<VirtualFile>): List<FileInfo> = uploadingFiles.mapNotNull { vFile ->
    val jFilePath = vFile.toJavaPath ?: return@mapNotNull null
    val jFile = File(jFilePath)
    LocalDriverManager.instance.createFileInfo(jFile)
  }

  private val VirtualFile.toJavaPath: String?
    get() = if (fileType is ArchiveFileType)
      canonicalPath?.removeSuffix("!/")
    else
      canonicalPath


  fun calculatePasteType(allowMove: Boolean,
                         infosForPaste: List<FileInfo>,
                         targetDriver: Driver): CopyOrMove {
    if (!allowMove)
      return CopyOrMove.COPY


    val isAllMove = infosForPaste.all { isMove(it, targetDriver) }
    return when {
      isAllMove -> CopyOrMove.MOVE
      else -> CopyOrMove.COPY
    }
  }

  private fun isMove(fromInfo: FileInfo, toDriver: Driver): Boolean = fromInfo.driver == toDriver
}