package io.confluent.kafka.core.rfs.projectview.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import io.confluent.kafka.core.rfs.driver.FileInfo

//class RfsEditSourceAction : RfsViewActionBase(requestFocus = true)
//class RfsViewSourceAction : RfsViewActionBase(requestFocus = false) {
//  override fun update(e: AnActionEvent) {
//    if (e.place != ActionPlaces.KEYBOARD_SHORTCUT) {
//      e.presentation.isEnabledAndVisible = false
//    }
//    else {
//      super.update(e)
//    }
//  }
//}

abstract class RfsViewActionBase(val requestFocus: Boolean) : RfsProjectPaneActionBase() {

  override fun actionPerformed(e: AnActionEvent) = withRfsPane(e) {
    val fileInfo = getSelectedFileInfo() ?: return
    //FileTypeViewerManager.getInstance(project).openViewer(fileInfo, requestFocus)
  }

  override fun update(e: AnActionEvent) = withRfsPane(e) {
    val fileInfo = getSelectedFileInfo()
    val hasViewableFile = isSingleDriverSelect() && fileInfo != null && canView(fileInfo)
    e.presentation.isVisible = hasViewableFile
    e.presentation.isEnabled = hasViewableFile && isLoaded() && isReadable(fileInfo)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  companion object {
    // 'viewer != null' condition removed as it requires remote query and the only case with 'viewer == null'
    // when there is a disabled (internal only) viewer - right now there are no such viewers
    fun canView(fileInfo: FileInfo) =
      fileInfo.isFile && fileInfo.driver.isFileStorage

    fun isReadable(fileInfo: FileInfo?) = fileInfo != null && fileInfo.permission?.readable != false
  }
}