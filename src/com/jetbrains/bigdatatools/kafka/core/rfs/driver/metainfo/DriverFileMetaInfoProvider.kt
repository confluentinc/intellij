package com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo

import com.intellij.openapi.Disposable
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo.details.FileInfoDetails
import com.jetbrains.bigdatatools.kafka.core.rfs.editorviewer.RfsTableColumn
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.DriverFileRfsTreeNode

interface DriverFileMetaInfoProvider {
  fun getDefaultTableColumns(): List<String>
  fun getAllTableColumns(): List<RfsTableColumn<*>>
  fun getFileDetails(rfsTreeNode: DriverFileRfsTreeNode, curWindowDisposable: Disposable): FileInfoDetails
  fun getDefaultComparator(): Comparator<FileInfo>
}