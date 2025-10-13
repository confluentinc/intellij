package io.confluent.intellijplugin.core.rfs.copypaste.providers

import com.intellij.ide.PasteProvider
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.copyhandler.InterDriverCopyManager
import io.confluent.intellijplugin.core.rfs.projectview.actions.RfsPaneOwner
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import java.util.concurrent.CancellationException

internal class RfsPasteProvider(private val pane: RfsPaneOwner) : PasteProvider, DumbAware {
    override fun isPasteEnabled(dataContext: DataContext): Boolean = true

    override fun isPastePossible(dataContext: DataContext): Boolean {
        if (pane.getSelectedDriverNodes().size != 1)
            return false
        try {
            val pastingFileInfos = RfsPasteProviderUtils.getPastingFileInfos() ?: return false
            val destInfo = getTargetFileInfo() ?: return false

            return InterDriverCopyManager.canCopyToFolder(pastingFileInfos, destInfo.path, destInfo.driver, false)
        } catch (t: Throwable) {
            if (t is ControlFlowException || t is CancellationException) throw t
            thisLogger().warn("Cannot be pasted", t)
            return false
        }

    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun performPaste(dataContext: DataContext) {
        val targetFileInfo = getTargetFileInfo() ?: return

        val allowMove = RfsPasteProviderUtils.getTransferableDescriptor()?.deleteAfter == true
        RfsPasteProviderUtils.processRfsPaste(pane.project, targetFileInfo, allowMove)
    }

    private fun getTargetFileInfo(): FileInfo? {
        val destNode = pane.getSelectedDriverNode() ?: return null
        val node = if (destNode.rfsPath.isDirectory)
            destNode
        else
            destNode.parent as? DriverFileRfsTreeNode
        return node?.fileInfo
    }
}