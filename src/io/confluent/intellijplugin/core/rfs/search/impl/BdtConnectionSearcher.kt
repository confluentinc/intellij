package io.confluent.intellijplugin.core.rfs.search.impl

import com.intellij.ide.projectView.impl.ProjectViewTree
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Processor
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowFactory
import io.confluent.intellijplugin.core.rfs.view.FileTypeViewerManager
import io.confluent.intellijplugin.core.settings.defaultui.UiUtil
import io.confluent.intellijplugin.core.util.BdtAsyncPromise
import org.jetbrains.concurrency.AsyncPromise

object BdtConnectionSearcher {
    fun getProjectViewTree(project: Project): ProjectViewTree? {
        val toolWindow =
            ToolWindowManager.getInstance(project).getToolWindow(BigDataToolWindowFactory.TOOL_WINDOW_ID) ?: return null
        return UiUtil.getFirstChildComponent(toolWindow.contentManager.component)
    }

    fun list(driver: Driver, rfsPath: RfsPath, batchId: String? = null): AsyncPromise<ListResult?> {
        val promise = BdtAsyncPromise<ListResult?>()

        try {
            driver.list(rfsPath, batchId, Processor {
                promise.setResult(it)
                false
            })
        } catch (t: Throwable) {
            promise.setError(t)
        }

        return promise
    }

    fun goToFound(connectionId: String, rfsPath: RfsPath, project: Project) {
        FileTypeViewerManager.getInstance(project).openSearchResult(connectionId, rfsPath)
    }
}