package io.confluent.kafka.core.rfs.copypaste.model

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import io.confluent.kafka.core.rfs.copypaste.dialog.RfsSkipOverwriteChoice
import io.confluent.kafka.core.util.SizeUtils
import io.confluent.kafka.util.KafkaMessagesBundle

data class RfsCopyMoveContext(val project: Project, val isCopy: Boolean) {
  var progressIndicator: ProgressIndicator? = null
    private set

  var totalCount: Int = -1
    set(value) {
      field = value
      progressIndicator?.isIndeterminate = value > 0
    }

  private var sourceFileName: String = ""

  @NlsSafe
  private var targetFileName: String? = null
  private var processedFiles: Int = 0

  private var fileSize: Long = 0
  private var proceededFileBytes: Long = 0

  var skipOverwriteChoice: RfsSkipOverwriteChoice? = null

  val isCanceled: Boolean
    get() = progressIndicator?.isCanceled == true

  var finishResult: Result<Unit>? = null


  fun setupProgressIndicator(progressIndicator: ProgressIndicator) {
    if (this.progressIndicator != null) {
      error("Cannot setup progress indicator if one is install")
    }

    progressIndicator.isIndeterminate = false

    (progressIndicator as? ProgressIndicatorEx)?.addStateDelegate(object : AbstractProgressIndicatorExBase() {
      override fun cancel() {
        notifyIndicator()
      }
    })

    this.progressIndicator = progressIndicator
  }

  fun startProceedFile(source: String, target: String?, size: Long) {
    if (totalCount == -1) {
      notifyFastIndicator()
      return
    }

    sourceFileName = source
    targetFileName = target
    fileSize = size
    proceededFileBytes = 0
    processedFiles++

    notifyIndicator()
  }

  fun addFileBytes(addedBytes: Long) {
    proceededFileBytes += addedBytes
    notifyIndicator()
  }

  fun updateBytes(newBytes: Long) {
    proceededFileBytes = newBytes
    notifyIndicator()
  }

  fun notifyIndicator() {

    progressIndicator?.text = targetFileName?.let {
      KafkaMessagesBundle.message("rfs.copy.process.text", processedFiles, totalCount, sourceFileName, it)
    } ?: sourceFileName

    if (progressIndicator?.isCanceled == true) {
      finishResult = Result.success(Unit)
      progressIndicator?.text2 = KafkaMessagesBundle.message("rfs.copy.process.text2.canceling")
      progressIndicator?.isIndeterminate = true
      return
    }

    progressIndicator?.text2 = when {
      isCopy && fileSize == -1L && proceededFileBytes == 0L -> KafkaMessagesBundle.message("copy.undefined")
      isCopy && fileSize == -1L -> KafkaMessagesBundle.message("copy.undefined.target", SizeUtils.toString(proceededFileBytes))
      isCopy -> KafkaMessagesBundle.message("copy.progress", SizeUtils.toString(proceededFileBytes), SizeUtils.toString(fileSize))

      fileSize == -1L && proceededFileBytes == 0L -> KafkaMessagesBundle.message("copy.undefined")
      fileSize == -1L -> KafkaMessagesBundle.message("copy.undefined.target", SizeUtils.toString(proceededFileBytes))
      else -> KafkaMessagesBundle.message("copy.progress", SizeUtils.toString(proceededFileBytes), SizeUtils.toString(fileSize))
    }

    when {
      totalCount == 1 && fileSize == -1L -> progressIndicator?.isIndeterminate = true
      fileSize == -1L -> progressIndicator?.fraction = processedFiles.toDouble() / totalCount
      else -> {
        progressIndicator?.isIndeterminate = false
        progressIndicator?.fraction = (processedFiles - 1).toDouble() / totalCount + (proceededFileBytes).toDouble() / fileSize / totalCount
      }
    }
  }

  private fun notifyFastIndicator() {
    progressIndicator?.isIndeterminate = true
    progressIndicator?.text = targetFileName?.let { KafkaMessagesBundle.message("rfs.copy.process.text.fast", sourceFileName, it) }
                              ?: sourceFileName
    progressIndicator?.text2 = ""
  }


  companion object {
    fun createSingle(project: Project,
                     progressIndicator: ProgressIndicator,
                     source: String,
                     target: String?,
                     size: Long,
                     isCopy: Boolean): RfsCopyMoveContext {
      val rfsCopyMoveContext = RfsCopyMoveContext(project = project,
                                                  isCopy = isCopy)
      rfsCopyMoveContext.setupProgressIndicator(progressIndicator)
      rfsCopyMoveContext.totalCount = 1

      rfsCopyMoveContext.startProceedFile(source, target, size)
      return rfsCopyMoveContext
    }

    fun create(project: Project, isCopy: Boolean) = RfsCopyMoveContext(project = project, isCopy = isCopy)
  }
}
