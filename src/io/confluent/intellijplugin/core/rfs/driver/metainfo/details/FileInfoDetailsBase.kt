package io.confluent.intellijplugin.core.rfs.driver.metainfo.details

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.metainfo.DirectorySizeAndCountComponentController
import io.confluent.intellijplugin.core.rfs.driver.metainfo.FileMetaInfoUtils
import io.confluent.intellijplugin.core.rfs.driver.metainfo.components.SelectableLabel
import io.confluent.intellijplugin.core.rfs.driver.metainfo.rowIfNotBlank
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.core.settings.defaultui.UiUtil
import io.confluent.intellijplugin.core.ui.MigPanel
import io.confluent.intellijplugin.core.util.toPresentableText
import io.confluent.intellijplugin.util.KafkaMessagesBundle
import java.util.concurrent.atomic.AtomicBoolean

open class FileInfoDetailsBase(val rfsTreeNode: DriverFileRfsTreeNode, parentDisposable: Disposable) : FileInfoDetails {
  val isDisposed = AtomicBoolean(false)

  init {
    @Suppress("LeakingThis")
    Disposer.register(parentDisposable, this)
  }

  override fun dispose() {
    isDisposed.set(true)
  }

  override fun getBlocks(): List<FileInfoBlock> {
    val fileInfo = rfsTreeNode.fileInfo
    val path = rfsTreeNode.rfsPath
    val panel = MigPanel(UiUtil.insets10FillXHidemode3).apply {
      row(KafkaMessagesBundle.message("file.info.label.path"), SelectableLabel(path.stringRepresentation()))
      row(KafkaMessagesBundle.message("file.info.label.type"), SelectableLabel(FileMetaInfoUtils.getPathType(path)))

      showErrorInfo(rfsTreeNode)

      if (fileInfo == null)
        return@apply

      rowIfNotBlank(KafkaMessagesBundle.message("file.info.label.size"), FileMetaInfoUtils.getFileSize(fileInfo))
      rowIfNotBlank(KafkaMessagesBundle.message("file.info.label.modification.time"), FileMetaInfoUtils.getFileModificationTime(fileInfo))
      rowIfNotBlank(KafkaMessagesBundle.message("file.info.label.permissions"), fileInfo.permission?.printString())

      showAdditionalComponents(fileInfo)

      createDirContentsRow(rfsTreeNode)
    }

    return listOf(FileInfoBlock("", panel))
  }

  protected open fun MigPanel.showAdditionalComponents(fileInfo: FileInfo) = Unit

  protected fun MigPanel.showErrorInfo(rfsTreeNode: DriverFileRfsTreeNode) {
    rowIfNotBlank(KafkaMessagesBundle.message("file.info.label.error"), rfsTreeNode.error?.toPresentableText())
  }

  protected fun MigPanel.createDirContentsRow(rfsTreeNode: DriverFileRfsTreeNode) {
    val project = rfsTreeNode.project ?: return
    val fileInfo = rfsTreeNode.fileInfo ?: return
    val rfsPath = fileInfo.path

    if (!rfsPath.isDirectory)
      return

    val component = DirectorySizeAndCountComponentController(project, fileInfo, this@FileInfoDetailsBase).component
    row(KafkaMessagesBundle.message("rfs.directory.contents.label"), component)
  }
}