package com.jetbrains.bigdatatools.kafka.core.monitoring.rfs

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.jetbrains.bigdatatools.kafka.core.connection.tunnel.model.RestClientData
import com.jetbrains.bigdatatools.kafka.core.monitoring.connection.MonitoringFileInfoManager
import com.jetbrains.bigdatatools.kafka.core.monitoring.data.MonitoringDataManager
import com.jetbrains.bigdatatools.kafka.core.monitoring.toolwindow.MonitoringToolWindowController
import com.jetbrains.bigdatatools.kafka.core.rfs.copypaste.model.RfsEstimateSizeCountContext
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.*
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.ErrorResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.OkResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo.DriverFileMetaInfoProvider
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.metainfo.DriverFileMetaInfoProviderBase
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsFileInfoChildren
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.DriverRfsTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.DriverWithCompoundsTreeModel
import com.jetbrains.bigdatatools.kafka.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import com.jetbrains.bigdatatools.kafka.core.util.BdIdeRegistryUtil
import com.jetbrains.bigdatatools.kafka.core.util.async.runOrAwait
import kotlinx.coroutines.*
import java.io.OutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.INFINITE
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class MonitoringDriver(override val project: Project?, val testConnection: Boolean) : AbstractDriver(), Disposable {
  override val root: RfsPath = RfsPath(emptyList(), true)
  override val isAvailableCopyThroughIoStreams: Boolean = false
  override val isFileStorage: Boolean = false
  override val isMkDirSupported: Boolean = true
  override val isCreateFileSupported: Boolean = false
  abstract val dataManager: MonitoringDataManager

  override val treeNodeBuilder: RfsDriverTreeNodeBuilder = object : RfsDriverTreeNodeBuilder() {
    override fun createNode(project: Project, path: RfsPath, driver: Driver) = MonitoringRfsTreeNode(project, root, this@MonitoringDriver)
  }

  override val timeout: Duration
    get() = (connectionData as? RestClientData)?.operationTimeout?.toIntOrNull()?.seconds
            ?: BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT.milliseconds


  override val presentableName: String
    get() = connectionData.name

  @Suppress("LeakingThis")
  override val fileInfoManager = MonitoringFileInfoManager(this)

  abstract fun getController(project: Project): MonitoringToolWindowController?

  override suspend fun refreshConnection(activitySource: ActivitySource): ReadyConnectionStatus {
    check(safeExecutor.coroutineScope.isActive) { "driver $this cannot be refreshed, it is shutting down" }
    return refreshProcess.runOrAwait(cancelPrevious = activitySource.calledByUser) {
      withTimeout(if (activitySource.calledByUser) INFINITE else timeout) {
        safeExecutor.computeDetached("Driver refresh") {
          withContext(Dispatchers.IO) {
            innerRefreshConnection(activitySource.calledByUser)
          }
        }
      }
    }
  }

  open fun innerRefreshConnection(calledByUser: Boolean): ReadyConnectionStatus {
    fileInfoManager.driverRefreshStarted()

    val status = try {
      dataManager.connect(calledByUser, testConnection) {
        prepareSlaveDriver()
      }
      ConnectedConnectionStatus
    }
    catch (t: Throwable) {
      FailedConnectionStatus(t)
    }

    fileInfoManager.driverRefreshFinished(status)

    if (status == ConnectedConnectionStatus) {
      dataManager.onSuccessfulConnect()
    }
    return status
  }

  fun isAvailable() = dataManager.client.getConnectionStatus()

  override fun initDriverUpdater() {
    refreshConnectionLaunch(ActivitySource.DRIVER_CREATION)
  }

  override fun listStatus(path: RfsPath, force: Boolean): SafeResult<RfsFileInfoChildren> = try {
    OkResult(RfsFileInfoChildren(doLoadChildren(path)))
  }
  catch (t: Throwable) {
    ErrorResult(t)
  }

  override fun getFileStatus(path: RfsPath, force: Boolean): SafeResult<FileInfo?> = try {
    OkResult(doLoadFileInfo(path))
  }
  catch (t: Throwable) {
    ErrorResult(t)
  }

  protected open fun doLoadChildren(rfsPath: RfsPath): List<FileInfo>? = emptyList()

  open fun doLoadFileInfo(rfsPath: RfsPath): FileInfo? = DummyFileInfo("", root, root.stringRepresentation(),
                                                                       this@MonitoringDriver)


  override fun createTreeModel(rootPath: RfsPath, project: Project): DriverRfsTreeModel =
    DriverWithCompoundsTreeModel(project, rootPath, this)

  override fun getMetaInfoProvider(): DriverFileMetaInfoProvider = DriverFileMetaInfoProviderBase(this)

  override fun mkdir(path: RfsPath): Result<Unit> = Result.success(Unit)
  override fun getHomeUri(): String = ""
  override fun getExternalId(): String = connectionData.innerId
  override fun getHomeInfo(): SafeResult<FileInfo?> = OkResult(null)
  override fun createRfsPath(path: String): RfsPath = RfsPath(path.split("/").filter { it.isNotBlank() }, path.endsWith("/"))

  override fun getWriteStream(rfsPath: RfsPath, overwrite: Boolean, canCreateNewFile: Boolean) =
    safeExecutor.asyncInterruptible("Opening write stream for '$rfsPath'") {
      doCreateWriteStream(rfsPath, overwrite, canCreateNewFile)
    }.blockingGet()

  open fun doCreateWriteStream(rfsPath: RfsPath, overwrite: Boolean, create: Boolean): OutputStream = throw UnsupportedOperationException()

  override fun estimateInfoForDirectory(rfsPath: RfsPath, context: RfsEstimateSizeCountContext) {}

  override fun allowSameNamedFilesAndDirectories(): Boolean = false
  override fun validatePath(path: RfsPath): String? = null

  override fun getCachedFileInfo(rfsPath: RfsPath): SafeResult<FileInfo?>? = if (rfsPath.isRoot) {
    when (val connectionStatus = fileInfoManager.getConnectionStatus()) {
      ConnectedConnectionStatus -> getFileStatus(rfsPath)
      ConnectingConnectionStatus -> null
      is FailedConnectionStatus -> ErrorResult(connectionStatus.getException())
    }
  }
  else {
    getFileStatus(rfsPath)
  }

  suspend fun waitConnect(): Boolean {
    if (dataManager.client.isConnected())
      return true

    return withTimeoutOrNull(timeout) {
      // TODO Could delay waiting be avoided? Receive state from channel? Flow?
      while (!dataManager.client.isInited() || dataManager.client.isConnecting()) {
        delay(200)
      }
      true
    } ?: false
  }
}