package com.jetbrains.bigdatatools.kafka.core.rfs.search.impl

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Processor
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.Driver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.projectview.toolwindow.BigDataToolWindowFactory
import com.jetbrains.bigdatatools.kafka.core.rfs.view.FileTypeViewerManager
import com.jetbrains.bigdatatools.kafka.core.settings.defaultui.UiUtil
import com.jetbrains.bigdatatools.kafka.core.util.BdtAsyncPromise
import org.jetbrains.concurrency.AsyncPromise

object BdtConnectionSearcher {
  fun getProjectViewTree(project: Project): ProjectViewTree? {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(BigDataToolWindowFactory.TOOL_WINDOW_ID) ?: return null
    return UiUtil.getFirstChildComponent(toolWindow.contentManager.component)
  }

  fun list(driver: Driver, rfsPath: RfsPath, batchId: String? = null): AsyncPromise<ListResult?> {
    val promise = BdtAsyncPromise<ListResult?>()

    try {
      driver.list(rfsPath, batchId, Processor {
        promise.setResult(it)
        false
      })
    }
    catch (t: Throwable) {
      promise.setError(t)
    }

    return promise
  }

  fun goToFound(connectionId: String, rfsPath: RfsPath, project: Project) {
    FileTypeViewerManager.getInstance(project).openSearchResult(connectionId, rfsPath)
  }
}