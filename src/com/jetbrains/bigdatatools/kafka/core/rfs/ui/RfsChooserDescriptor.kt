package io.confluent.kafka.core.rfs.ui

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import io.confluent.kafka.core.rfs.driver.FileInfo

open class RfsChooserDescriptor(val multipleSelection: Boolean = false) {
  open fun canChoose(fileInfo: FileInfo): Boolean = true
  open fun toolbarActions(): ActionGroup {
    return ActionManager.getInstance().getAction("BigDataTools.RfsFileChooserActionGroup") as ActionGroup
  }
}

class RfsDirOnlyDescriptor(multipleSelection: Boolean) : RfsChooserDescriptor(multipleSelection) {
  override fun canChoose(fileInfo: FileInfo): Boolean = fileInfo.isDirectory
}