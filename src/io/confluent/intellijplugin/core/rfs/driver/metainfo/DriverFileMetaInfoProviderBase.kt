package io.confluent.intellijplugin.core.rfs.driver.metainfo

import com.intellij.openapi.Disposable
import io.confluent.intellijplugin.core.rfs.driver.Driver
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.metainfo.details.FileInfoDetailsBase
import io.confluent.intellijplugin.core.rfs.editorviewer.RfsTableColumn
import io.confluent.intellijplugin.core.rfs.tree.node.DriverFileRfsTreeNode
import io.confluent.intellijplugin.util.KafkaMessagesBundle

open class DriverFileMetaInfoProviderBase(open val driver: Driver) : DriverFileMetaInfoProvider {
  override fun getDefaultComparator(): Comparator<FileInfo> = compareBy({ !it.isDirectory }, { it.name })

  override fun getDefaultTableColumns(): List<String> = listOf(TYPE_COLUMN, MODIFIED_COLUMN, SIZE_COLUMN)

  override fun getAllTableColumns(): List<RfsTableColumn<*>> = listOf(
    object : RfsTableColumn<String>(KafkaMessagesBundle.message("column.name.type"), TYPE_COLUMN, columns = 8) {
      override fun valueForFileInfo(fileInfo: FileInfo): String = FileMetaInfoUtils.getPathType(fileInfo.path)
      override fun getColumnComparable(): (FileInfo) -> Comparable<*>? = { valueForFileInfo(it) }
    },
    object : RfsTableColumn<Long>(KafkaMessagesBundle.message("column.name.size"), SIZE_COLUMN, columns = 6) {
      override fun valueForFileInfo(fileInfo: FileInfo): Long? = if (fileInfo.isFile) fileInfo.length else null
      override fun getColumnComparable(): (FileInfo) -> Comparable<*>? = { it.length }
    },
    object : RfsTableColumn<String>(KafkaMessagesBundle.message("column.name.modification.time"), MODIFIED_COLUMN, columns = 19) {
      override fun valueForFileInfo(fileInfo: FileInfo): String = FileMetaInfoUtils.getFileModificationTime(fileInfo) ?: ""
      override fun getColumnComparable(): (FileInfo) -> Comparable<*>? = { it.modificationTime }
    }
  )

  override fun getFileDetails(rfsTreeNode: DriverFileRfsTreeNode,
                              curWindowDisposable: Disposable) = FileInfoDetailsBase(rfsTreeNode, curWindowDisposable)

  companion object {
    private const val TYPE_COLUMN = "type"
    private const val SIZE_COLUMN = "size"
    private const val MODIFIED_COLUMN = "modified"
  }
}