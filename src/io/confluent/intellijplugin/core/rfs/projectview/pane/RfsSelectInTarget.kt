package io.confluent.intellijplugin.core.rfs.projectview.pane

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl
import io.confluent.intellijplugin.core.rfs.driver.manager.DriverManager
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsViewerEditorProvider
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowController
import io.confluent.intellijplugin.core.rfs.projectview.toolwindow.BigDataToolWindowFactory
import io.confluent.intellijplugin.core.rfs.util.RfsFileUtil
import io.confluent.intellijplugin.core.rfs.util.RfsUtil
import io.confluent.intellijplugin.util.KafkaMessagesBundle

class RfsSelectInTarget : SelectInTarget, DumbAware {
    override fun canSelect(context: SelectInContext?): Boolean {
        val virtualFile = context?.virtualFile ?: return false

        return isRfsEditorViewer(virtualFile) ||
                RfsFileUtil.getDriverId(virtualFile) != null && RfsFileUtil.getPath(virtualFile) != null
    }

    override fun selectIn(context: SelectInContext?, requestFocus: Boolean) {
        val project = context?.project ?: return
        val mainPane = BigDataToolWindowController.getInstance(project)?.getMainPane() ?: return
        val toolWindowManager = ToolWindowManager.getInstance(project) as ToolWindowManagerImpl
        if (toolWindowManager.activeToolWindowId != BigDataToolWindowFactory.TOOL_WINDOW_ID) {
            toolWindowManager.showToolWindow(BigDataToolWindowFactory.TOOL_WINDOW_ID)
        }
        val paneOwner = mainPane.actionsOwner
        val virtualFile = context.virtualFile

        if (isRfsEditorViewer(virtualFile))
            selectForRfsEditorView(virtualFile, paneOwner)
        else
            selectForFile(virtualFile, paneOwner)
    }

    private fun isRfsEditorViewer(virtualFile: VirtualFile) =
        virtualFile.getUserData(RfsViewerEditorProvider.RFS_EDITOR_VIEWER_SELECTED_PATH) != null

    private fun selectForFile(
        virtualFile: VirtualFile,
        paneOwner: RfsTreePaneOwner
    ) {
        val targetDriverId = RfsFileUtil.getDriverId(virtualFile) ?: return
        val targetDriver = DriverManager.getDriverById(paneOwner.project, targetDriverId) ?: return
        val targetPathString = RfsFileUtil.getPath(virtualFile) ?: return
        val targetPath = targetDriver.createRfsPath(targetPathString)
        RfsUtil.select(targetDriver.getExternalId(), targetPath, paneOwner)
    }

    private fun selectForRfsEditorView(virtualFile: VirtualFile, paneOwner: RfsTreePaneOwner) {
        val paths = virtualFile.getUserData(RfsViewerEditorProvider.RFS_EDITOR_VIEWER_SELECTED_PATH)
        val driver = virtualFile.getUserData(RfsViewerEditorProvider.RFS_EDITOR_VIEWER_DRIVER) ?: return
        paths?.forEach {
            RfsUtil.select(driver.getExternalId(), it, paneOwner)
        }
    }

    override fun toString(): String = KafkaMessagesBundle.message("action.show.in.rfs.pane")
}