package io.confluent.intellijplugin.core.rfs.copypaste.providers

import com.intellij.ide.CopyPasteManagerEx
import com.intellij.ide.CopyProvider
import com.intellij.ide.CutProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.rfs.copypaste.model.RfsNodeTransferable
import io.confluent.intellijplugin.core.rfs.copypaste.model.TransferableDescriptor
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsPaneOwner

internal class RfsCopyCutProvider(private val pane: RfsPaneOwner) : CutProvider, CopyProvider, DumbAware {
  override fun isCutVisible(dataContext: DataContext): Boolean = true
  override fun isCutEnabled(dataContext: DataContext): Boolean {
    val nodes = pane.getSelectedDriverNodes()
    return nodes.isNotEmpty() && nodes.all {
      !it.isMount && it.fileInfo?.isMoveSupport == true
    }
  }

  override fun performCut(dataContext: DataContext) = saveContent(true)

  override fun isCopyVisible(dataContext: DataContext): Boolean = true

  override fun isCopyEnabled(dataContext: DataContext): Boolean = pane.getSelectedDriverNodes().isNotEmpty() &&
                                                                  !pane.getSelectedDriverNodes().any {
                                                                    it.fileInfo == null ||
                                                                    it.fileInfo?.isCopySupport == false
                                                                  }

  override fun performCopy(dataContext: DataContext) = saveContent(false)

  private fun saveContent(deleteAfter: Boolean) {
    val copyPasteManager = CopyPasteManagerEx.getInstanceEx()

    val selected = pane.getSelectedDriverNodes()
    copyPasteManager.allContents.filterIsInstance<RfsNodeTransferable>().forEach {
      copyPasteManager.removeContent(it)
    }

    val rfsNodeTransferable = RfsNodeTransferable(TransferableDescriptor(selected, deleteAfter))
    copyPasteManager.setContents(rfsNodeTransferable)
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}