package com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo

import com.intellij.CommonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsEstimateSizeCountContext
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.ui.MigPanel
import com.jetbrains.bigdatatools.kafka.core.util.SizeUtils
import com.jetbrains.bigdatatools.kafka.core.util.toPresentableText
import com.jetbrains.bigdatatools.kafka.util.KafkaMessagesBundle
import javax.swing.JLabel

class DirectorySizeAndCountComponentController(val project: Project,
                                               val fileInfo: FileInfo,
                                               private val parentDisposable: Disposable) : Disposable {
  init {
    Disposer.register(parentDisposable, this)
  }

  private val driver = fileInfo.driver
  private val rfsPath = fileInfo.path

  private var curContext: RfsEstimateSizeCountContext? = null

  private val infoLabel = JLabel("")
  private val errorLabel = JLabel("")
  private val loadingLabel = JLabel(AnimatedIcon.Default())

  private val startLink = ActionLink(KafkaMessagesBundle.message("rfs.directory.size.action.link.start")) {
    start()
  }

  private val cancelLink = ActionLink(CommonBundle.getCancelButtonText()) {
    stop()
    errorLabel.text = KafkaMessagesBundle.message("rfs.directory.task.estimate.contents.error.aborted")
  }

  val component = MigPanel().apply {
    add(startLink)
  }

  override fun dispose() = Unit

  private fun start() {
    errorLabel.text = ""
    infoLabel.text = ""

    setLoadingComponents()

    val context = calculateDirectoryInfo()
    curContext?.cancel()
    curContext = context

    context.progressNotifier.plusAssign { totalCount, totalSize ->
      val message = KafkaMessagesBundle.message("rfs.directory.task.estimate.contents.info", totalCount, SizeUtils.toString(totalSize))
      infoLabel.text = message
    }

    context.finishNotifier.plusAssign {
      curContext?.cancel()
      curContext = null

      if (it != null)
        errorLabel.text = it.toPresentableText()
      stop()
    }
  }

  private fun stop() {
    curContext?.cancel()
    setLoadedComponent()
  }

  private fun calculateDirectoryInfo(): RfsEstimateSizeCountContext {
    val task = object : Task.Backgroundable(project, KafkaMessagesBundle.message("rfs.directory.task.estimate.contents.title")) {
      override fun run(indicator: ProgressIndicator) {
        val curContext = (indicator as BackgroundableProcessIndicator).getUserData(RfsEstimateSizeCountContext.KEY)!!
        try {
          Disposer.register(parentDisposable) {
            indicator.cancel()
          }
          driver.estimateInfoForDirectory(rfsPath, curContext)
          curContext.notifyFinish(null)
        }
        catch (t: Throwable) {
          curContext.notifyFinish(t)
        }
      }
    }

    val processIndicator = BackgroundableProcessIndicator(task)
    val context = RfsEstimateSizeCountContext(processIndicator)
    context.updateCalculatingPath(rfsPath.stringRepresentation())
    processIndicator.putUserData(RfsEstimateSizeCountContext.KEY, context)

    ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, processIndicator)
    return context
  }

  private fun setLoadingComponents() {
    component.apply {
      removeAll()
      add(loadingLabel)
      add(infoLabel)
      add(cancelLink)
      revalidate()
      repaint()
    }
  }

  private fun setLoadedComponent() {
    component.apply {
      removeAll()
      add(infoLabel)
      add(errorLabel)
      revalidate()
      repaint()
    }
  }
}