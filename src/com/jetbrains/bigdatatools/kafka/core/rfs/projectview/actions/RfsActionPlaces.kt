package io.confluent.kafka.core.rfs.projectview.actions

import com.intellij.openapi.actionSystem.ActionPlaces

object RfsActionPlaces {
  // toolbar in tool window
  const val TOOLWINDOW_TOOLBAR = "BDTToolWindow"
  // toolbar in file chooser
  const val FILE_CHOOSER_TOOLBAR = "RfsFileChooserDialog"
  // popup in tool window and file chooser
  val RFS_PANE_POPUP = ActionPlaces.getPopupPlace("RfsPanePopup")


  // toolbar in table view
  const val RFS_VIEWER_EDITOR = "RfsViewerEditor"
  // popup in table view
  val RFS_TABLE_VIEW_POPUP = ActionPlaces.getPopupPlace(RFS_VIEWER_EDITOR)
}