package io.confluent.kafka.core.rfs.copypaste

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import io.confluent.kafka.core.rfs.copypaste.dialog.RfsSkipOverwriteChoice
import io.confluent.kafka.core.rfs.copypaste.model.RfsCopyMoveContext
import io.confluent.kafka.core.rfs.copypaste.model.RfsEstimateSizeCountContext
import io.confluent.kafka.core.rfs.copypaste.utils.RfsUserConfirmatory
import io.confluent.kafka.core.rfs.driver.Driver
import io.confluent.kafka.core.rfs.driver.ExportFormat
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.task.RemoteFsMoveTask
import io.confluent.kafka.core.rfs.util.RfsNotificationUtils
import io.confluent.kafka.util.KafkaMessagesBundle

class CopyMoveTask(project: Project,
                   private val targetDriver: Driver,
                   private val targetFolderPath: RfsPath,
                   private val overrideTargetName: String?,
                   val exportFormat: ExportFormat?,
                   val additionalParams: Map<String, Any> = emptyMap(),
                   private val sourceFileInfos: List<FileInfo>,
                   private val move: Boolean,
                   private val onFileLoad: suspend (List<RfsPath>) -> Unit) : Task.Backgroundable(project, "CalculateLater") {
  init {
    title = calculateTitle()
  }

  val copyMoveContext = RfsCopyMoveContext.create(project, move).also {
    if (additionalParams[RfsCopyPasteParams.FORCE_OVERWRITE] == true)
      it.skipOverwriteChoice = RfsSkipOverwriteChoice.OVERWRITE_ALL
  }

  override fun run(indicator: ProgressIndicator) = try {
    copyMoveContext.setupProgressIndicator(indicator)
    indicator.isIndeterminate = true
    innerRun(indicator)

    copyMoveContext.finishResult = Result.success(Unit)
  }
  catch (t: Throwable) {
    copyMoveContext.finishResult = Result.failure(t)
    RfsNotificationUtils.notifyException(t, title)
  }

  private fun innerRun(indicator: ProgressIndicator) {
    if (move)
      move(indicator)
    else
      copy(indicator)

    val uploadedFileNames = sourceFileInfos.map {
      overrideTargetName ?: RfsCopyPasteUtil.getCorrectTargetPath(it, it.path, targetDriver, exportFormat).name
    }
    val refreshPaths = uploadedFileNames.zip(getRefreshIsDir()).map {
      targetFolderPath.child(it.first, it.second)
    }

    runBlockingCancellable {
      onFileLoad(refreshPaths)
    }
  }

  private fun move(indicator: ProgressIndicator) {
    sourceFileInfos.forEach {
      if (it.driver != targetDriver)
        error(KafkaMessagesBundle.message("move.not.alowed.between.different.connections"))
    }

    val tasks: List<RemoteFsMoveTask> = sourceFileInfos.map {
      val targetPath = targetFolderPath.child(overrideTargetName ?: it.name, it.isDirectory)
      it.renameAsync(targetPath, true).resultOrThrow()
    }

    val userInfoMessage = tasks.firstOrNull()?.moveUserInfoMessage()
    val canContinue = RfsUserConfirmatory.askCanContinue(project, userInfoMessage, sourceFileInfos.first().driver.getExternalId())
    if (!canContinue)
      return

    val isFast = tasks.none { it.isNeedPrecalculate() }

    if (isFast) {
      copyMoveContext.startProceedFile("", "", -1)
    }
    else {
      val estimateSizeCountContext = estimateFolderSize(indicator)
      copyMoveContext.totalCount = estimateSizeCountContext.totalCount
    }
    copyMoveContext.notifyIndicator()

    if (copyMoveContext.isCanceled)
      return

    tasks.forEach {
      it.run(copyMoveContext)
    }
  }

  private fun copy(indicator: ProgressIndicator) {
    val estimateSizeCountContext = estimateFolderSize(indicator)
    copyMoveContext.totalCount = estimateSizeCountContext.totalCount
    copyMoveContext.notifyIndicator()
    val userInfoMessage = sourceFileInfos.firstNotNullOfOrNull { sourceFileInfo ->
      if (indicator.isCanceled)
        return
      RfsCopyPasteService.getUserConfirmMessageForFolder(sourceFileInfo, targetDriver)
    }
    val key = sourceFileInfos.first().driver.getExternalId() + "->" + targetDriver.getExternalId()
    val canContinue = RfsUserConfirmatory.askCanContinue(project, userInfoMessage, key)
    if (!canContinue)
      return

    sourceFileInfos.forEach { sourceFileInfo ->
      if (indicator.isCanceled)
        return
      val copyTask = RfsCopyPasteService.createCopyTaskToFolder(sourceFileInfo,
                                                                targetFolderPath,
                                                                targetDriver,
                                                                exportFormat,
                                                                overrideTargetName,
                                                                additionalParams)
      try {
        copyTask.run(copyMoveContext)
      }
      catch (t: Throwable) {
        //Refresh files if smth go wrong because smth can be changed
        targetDriver.fileInfoManager.refreshFiles(targetFolderPath)
        throw t
      }
      targetDriver.fileInfoManager.waitAppear(copyTask.rootToPath)
    }
  }

  private fun estimateFolderSize(indicator: ProgressIndicator): RfsEstimateSizeCountContext {
    val estimateSizeCountContext = RfsEstimateSizeCountContext(indicator)

    sourceFileInfos.forEach {
      estimateSizeCountContext.updateCalculatingPath(it.path.stringRepresentation())
      if (it.isFile)
        estimateSizeCountContext.updateSizeCount(1, it.length)
      else
        it.driver.estimateInfoForDirectory(it.path, estimateSizeCountContext)
    }
    return estimateSizeCountContext
  }

  private fun getRefreshIsDir(): List<Boolean> = sourceFileInfos.map { it.isDirectory }

  private fun calculateTitle(): String {
    val type = if (move) {
      KafkaMessagesBundle.message("rfs.move.title")
    }
    else {
      KafkaMessagesBundle.message("rfs.copy.title")
    }

    val name = when {
      sourceFileInfos.size == 1 -> sourceFileInfos.first().path.name
      sourceFileInfos.all { it.path.isDirectory } -> KafkaMessagesBundle.message("rfs.directories.title")
      sourceFileInfos.all { it.path.isFile } -> KafkaMessagesBundle.message("rfs.files.title")
      else -> KafkaMessagesBundle.message("rfs.files.or.directories.title")
    }

    return "$type $name"
  }
}