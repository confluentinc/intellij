package com.jetbrains.bigdatatools.kafka.core.monitoring.connection

import com.jetbrains.bigdatatools.kafka.core.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.kafka.core.monitoring.rfs.MonitoringDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.ActivitySource
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.DriverConnectionStatus
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.FileInfo
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.RfsPath
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.DriverFileInfoManager
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.DriverRfsListener
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsChildrenPartId
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsFileInfoChildren
import kotlinx.coroutines.flow.Flow

class MonitoringFileInfoManager(override val driver: MonitoringDriver) : DriverFileInfoManager() {
  val dataManager: MonitoringDataManager
    get() = driver.dataManager

  override fun getDriverConnectionStatus(): DriverConnectionStatus = dataManager.getConnectionStatus()
  override fun notify(body: (DriverRfsListener) -> Unit) = driver.notify(body)

  override fun dispose() {}

  override fun loadFileInfo(rfsPath: RfsPath, force: Boolean): SafeResult<FileInfo?> = TODO("Should be not used")
  override fun getChildren(pageKey: RfsChildrenPartId, force: Boolean): Flow<SafeResult<RfsFileInfoChildren>> = TODO("Should be not used")

  override fun invokeLoadFileInfo(rfsPath: RfsPath) {}
  override fun refreshFiles(path: RfsPath) = notify {
    it.treeUpdated(path)
  }

  override suspend fun refreshDriver(activitySource: ActivitySource) {
    invokeDriverRefresh(activitySource)

    notify {
      it.nodeUpdated(driver.root)
    }
  }

  override fun waitAppear(rfsPath: RfsPath) {}
  override fun waitDisappear(rfsPath: RfsPath) {}

  override fun getCachedFileInfoInner(rfsPath: RfsPath) = TODO("Should be not used")
  override fun getCachedChildrenInner(rfsPath: RfsPath) = TODO("Should be not used")
}