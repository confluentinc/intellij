package io.confluent.kafka.core.rfs.driver.metainfo

import com.intellij.openapi.Disposable
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.metainfo.details.FileInfoDetails
import io.confluent.kafka.core.rfs.editorviewer.RfsTableColumn
import io.confluent.kafka.core.rfs.tree.node.DriverFileRfsTreeNode

interface DriverFileMetaInfoProvider {
  fun getDefaultTableColumns(): List<String>
  fun getAllTableColumns(): List<RfsTableColumn<*>>
  fun getFileDetails(rfsTreeNode: DriverFileRfsTreeNode, curWindowDisposable: Disposable): FileInfoDetails
  fun getDefaultComparator(): Comparator<FileInfo>
}