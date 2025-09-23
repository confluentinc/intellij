package io.confluent.kafka.core.monitoring.connection

import io.confluent.kafka.core.monitoring.data.MonitoringDataManager
import io.confluent.kafka.core.monitoring.rfs.MonitoringDriver
import io.confluent.kafka.core.rfs.driver.ActivitySource
import io.confluent.kafka.core.rfs.driver.DriverConnectionStatus
import io.confluent.kafka.core.rfs.driver.FileInfo
import io.confluent.kafka.core.rfs.driver.RfsPath
import io.confluent.kafka.core.rfs.driver.fileinfo.SafeResult
import io.confluent.kafka.core.rfs.fileInfo.DriverFileInfoManager
import io.confluent.kafka.core.rfs.fileInfo.DriverRfsListener
import io.confluent.kafka.core.rfs.fileInfo.RfsChildrenPartId
import io.confluent.kafka.core.rfs.fileInfo.RfsFileInfoChildren
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