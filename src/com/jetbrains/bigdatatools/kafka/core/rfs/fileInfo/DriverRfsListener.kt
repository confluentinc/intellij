package com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo

import com.jetbrains.bigdatatools.kafka.core.rfs.driver.DriverConnectionStatus
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult

interface DriverRfsListener {
  fun nodeUpdated(path: RfsPath) {}
  fun fileInfoLoaded(path: RfsPath, fileInfo: SafeResult<FileInfo?>) {}
  fun treeUpdated(path: RfsPath) {}
  fun childrenLoaded(path: RfsPath, children: SafeResult<RfsFileInfoChildren>) {}
  fun driverRefreshFinished(status: DriverConnectionStatus) {}
}