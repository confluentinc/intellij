package io.confluent.intellijplugin.core.rfs.fileInfo

import io.confluent.intellijplugin.core.rfs.driver.DriverConnectionStatus
import io.confluent.intellijplugin.core.rfs.driver.FileInfo
import io.confluent.intellijplugin.core.rfs.driver.RfsPath
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult

interface DriverRfsListener {
  fun nodeUpdated(path: RfsPath) {}
  fun fileInfoLoaded(path: RfsPath, fileInfo: SafeResult<FileInfo?>) {}
  fun treeUpdated(path: RfsPath) {}
  fun childrenLoaded(path: RfsPath, children: SafeResult<RfsFileInfoChildren>) {}
  fun driverRefreshFinished(status: DriverConnectionStatus) {}
}