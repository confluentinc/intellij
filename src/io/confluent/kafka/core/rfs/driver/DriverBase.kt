package io.confluent.kafka.core.rfs.driver

import com.intellij.openapi.progress.CeProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.Processor
import io.confluent.kafka.core.connection.exception.BdtConnectionException
import io.confluent.kafka.core.connection.exception.BdtDriverNotInitializedException
import io.confluent.kafka.core.rfs.copypaste.model.RfsEstimateSizeCountContext
import io.confluent.kafka.core.rfs.driver.fileinfo.OkResult
import io.confluent.kafka.core.rfs.driver.fileinfo.SafeResult
import io.confluent.kafka.core.rfs.driver.fileinfo.toKotlinResult
import io.confluent.kafka.core.rfs.driver.local.LocalDriver
import io.confluent.kafka.core.rfs.driver.metainfo.DriverFileMetaInfoProvider
import io.confluent.kafka.core.rfs.driver.metainfo.DriverFileMetaInfoProviderBase
import io.confluent.kafka.core.rfs.fileInfo.*
import io.confluent.kafka.core.rfs.fileInfo.impl.DriverCacheFileInfoManager
import io.confluent.kafka.core.rfs.search.impl.ListResult
import io.confluent.kafka.core.rfs.search.impl.SearchResult
import io.confluent.kafka.core.rfs.tree.DriverRfsTreeModel
import io.confluent.kafka.core.rfs.tree.SmartDriverRfsTreeModel
import io.confluent.kafka.core.rfs.tree.node.RfsDriverTreeNodeBuilder
import io.confluent.kafka.core.rfs.url.UrlFileOpener
import io.confluent.kafka.core.util.*
import io.confluent.kafka.core.util.async.runOrAwait
import io.confluent.kafka.util.KafkaMessagesBundle
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.future.asCompletableFuture
import java.io.OutputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource
import kotlin.time.measureTime

abstract class DriverBase : AbstractDriver() {
  override val isFileStorage: Boolean = true
  override val isMkDirSupported: Boolean = true
  override val isCreateFileSupported: Boolean = true

  override val timeout: Duration get() = BdIdeRegistryUtil.RFS_DEFAULT_TIMEOUT.milliseconds
  private val fileInfoLoader: DriverFileInfoLoader by lazy {
    object : DriverFileInfoLoader(project, this@DriverBase) {
      override fun doLoadFileInfo(rfsPath: RfsPath): FileInfo? = this@DriverBase.doGetFileStatus(rfsPath)
      override fun doLoadChildrenFileInfos(id: RfsChildrenPartId): Flow<RfsFileInfoChildren> = this@DriverBase.doListStatus(id)
      override fun notify(body: (DriverRfsListener) -> Unit) = this@DriverBase.notify(body)
    }
  }
  override val fileInfoManager: DriverFileInfoManager by lazy {
    DriverCacheFileInfoManager(this, fileInfoLoader)
  }

  override val treeNodeBuilder: RfsDriverTreeNodeBuilder = RfsDriverTreeNodeBuilder()

  private var currentHomeUri: String? = null

  override val root: RfsPath = RfsPath(emptyList(), true)

  override fun dispose() {}

  override fun getMetaInfoProvider(): DriverFileMetaInfoProvider = DriverFileMetaInfoProviderBase(this)

  override fun createTreeModel(rootPath: RfsPath, project: Project): DriverRfsTreeModel = SmartDriverRfsTreeModel(project, rootPath, this)

  private val innerConnectionStatus = MutableStateFlow<DriverConnectionStatus>(FailedConnectionStatus(BdtDriverNotInitializedException()))
  val connectionStatus: StateFlow<DriverConnectionStatus> get() = innerConnectionStatus

  override suspend fun refreshConnection(activitySource: ActivitySource): ReadyConnectionStatus {
    check(safeExecutor.coroutineScope.isActive) { "driver $this cannot be refreshed, it is shutting down" }
    val refreshStarted = TimeSource.Monotonic.markNow()
    return try {
      refreshProcess.runOrAwait(cancelPrevious = activitySource.calledByUser) {
        runCatching {
          fileInfoManager.driverRefreshStarted()
        }.onFailure {
          logger.error(it)
        }
        withContext(Dispatchers.IO) {
          prepareSlaveDriver()
        }
        try {
          withTimeout(if (activitySource.calledByUser) Duration.INFINITE else timeout) {
            safeExecutor.computeDetached("Driver refresh") {
              innerRefreshConnection(activitySource.calledByUser)
            }
          }
        }
        catch (e: CancellationException) {
          fileInfoManager.driverRefreshFinished(FailedConnectionStatus(CeProcessCanceledException(e)))
          throw e
        }
        catch (e: Throwable) {
          if (e !is BdtConnectionException) {
            logger.error(e)
          }
          FailedConnectionStatus(e)
        }
      }.also { result ->
        innerConnectionStatus.value = result
        fileInfoManager.driverRefreshFinished(result)
      }
    }
    catch (e: CancellationException) {
      // our goal is:
      // - not to return CancellationException from refreshConnection, instead return failure
      // - call driverRefreshFinished strictly before driverRefreshStarted of consequent invocation
      // - pass ProcessCanceledException instead of CancellationException to fus (because kotlin exceptions are not allowed to report)
      FailedConnectionStatus(CeProcessCanceledException(e))
    }
  }

  suspend fun autoRefresh(delay: Duration): Nothing {
    val driver = this
    coroutineScope {
      val driverStatusPaused = connectionStatus.mapStateIn(this) { status ->
        !status.isConnected() && !status.shouldReconnect()
      }

      BdtRefresherService.getInstance()
        .driverRefreshSchedule
        .subscribeImmediately()
        .pausedBy(driverStatusPaused to delay)
        .collect {
          if (driver.isAvailable().shouldReconnect()) {
            driver.refreshConnection(ActivitySource.TIMER)
          }
        }
      error("Unreachable")
    }
  }

  override fun initDriverUpdater() {
    safeExecutor.coroutineScope.launch {
      refreshConnection(ActivitySource.DRIVER_CREATION)
      autoRefresh(delay = 60.seconds)
    }
    fileInfoManager.startFileRefresh()
  }

  override fun estimateInfoForDirectory(rfsPath: RfsPath, context: RfsEstimateSizeCountContext) {
    if (context.isCanceled)
      return

    //TODO: Add collect for all children
    val fileInfos = listStatus(rfsPath, force = false).result?.fileInfos ?: return
    val (files, directories) = fileInfos.partition { it.isFile }
    val size = files.sumOf { it.length }
    val count = files.size
    context.updateSizeCount(count, size)

    directories.forEach {
      estimateInfoForDirectory(it.path, context)
    }
  }

  protected abstract fun doRefreshConnection(calledByUser: Boolean)

  suspend fun isAvailable(): DriverConnectionStatus {
    val status = try {
      withContext(Dispatchers.IO) {
        doIsAvailable()
      }
    }
    catch (t: Throwable) {
      FailedConnectionStatus(t)
    }
    innerConnectionStatus.value = status
    return status
  }

  final override fun listStatus(path: RfsPath, force: Boolean) = fileInfoManager.getChildren(path, force)

  final override fun listStatusAsync(path: RfsPath,
                                     force: Boolean,
                                     startFrom: RfsListMarker?,
                                     resultProcessor: Processor<SafeResult<RfsFileInfoChildren>>): CompletableFuture<*> {
    return safeExecutor.asyncSuspendProgress(ProgressOptions(
      taskName = KafkaMessagesBundle.message ("rfs.progress.title.listing.children", path),
      project = project,
      cancellable = true,
      showProgress = BdIdeRegistryUtil.RFS_LOAD_SHOW_PROGRESS
    )) {
      while (fileInfoManager.getConnectionStatus().isConnecting()) {
        delay(200)
      }
      fileInfoManager.getChildren(RfsChildrenPartId(path, startFrom), force)
        .run {
          flow {
            // TODO can't use kotlin.collections.withIndex() because of classloading problems ???
            var index = 0
            collect { value ->
              emit(IndexedValue(index++, value))
            }
          }
        }
        // we skip all errors including interruptions if there was at least one successful result before
        .takeWhile { (i, it) -> if (i == 0 || it is OkResult) resultProcessor.process(it) && (it is OkResult) else false }
        .collect()
    }.deferred.asCompletableFuture()
  }

  final override fun getFileStatus(path: RfsPath, force: Boolean) = fileInfoManager.loadFileInfo(path, force)

  override fun getCachedFileInfo(rfsPath: RfsPath) = fileInfoManager.getCachedFileInfo(rfsPath)

  protected abstract fun doCheckAvailable(): ReadyConnectionStatus
  protected open fun doIsAvailable(): DriverConnectionStatus = doCheckAvailable()

  protected fun checkHomeInfo(): ReadyConnectionStatus {
    return if (doGetHomeInfo() != null)
      ConnectedConnectionStatus
    else
      FailedConnectionStatus(Exception(KafkaMessagesBundle.message ("local.driver.root.not.found")))
  }

  override fun getWriteStream(rfsPath: RfsPath, overwrite: Boolean, canCreateNewFile: Boolean): SafeResult<OutputStream> =
    safeExecutor.asyncInterruptible<OutputStream>(taskName = "Opening write stream for '$rfsPath'", timeout = timeout * 2) {
      if (rfsPath.isDirectory) {
        throw IllegalArgumentException(KafkaMessagesBundle.message("rfs.error.no.read.stream.for.directory", rfsPath))
      }
      val fileInfo = getFileStatus(rfsPath).resultOrThrow()
      if (fileInfo == null && !canCreateNewFile) {
        throw IllegalArgumentException(KafkaMessagesBundle.message("do.not.exists", rfsPath))
      }
      if (fileInfo != null && !overwrite)
        throw IllegalArgumentException(KafkaMessagesBundle.message("rfs.error.file.already.exists", rfsPath))
      val innerStream = doCreateWriteStream(rfsPath, overwrite, canCreateNewFile)
      object : DelegatingOutputStream(innerStream) {
        override fun close() {
          try {
            innerStream.close()
          }
          finally {
            fileInfoManager.clearFileInfoCaches(listOf(rfsPath))
          }
          fileInfoManager.waitAppear(rfsPath)
        }
      }
    }.blockingGet()

  abstract fun doCreateWriteStream(rfsPath: RfsPath, overwrite: Boolean, create: Boolean): OutputStream

  private fun refreshHomeUri() {
    currentHomeUri = doGetHomeUri()
  }

  final override fun getHomeUri(): String = currentHomeUri ?: ""

  @NlsSafe
  abstract fun doGetHomeUri(): String

  final override fun getHomeInfo(): SafeResult<FileInfo?> =
    safeExecutor.asyncInterruptible(taskName = "Getting home info on ${this.presentableName}") {
      doGetHomeInfo()
    }.blockingGet()


  protected open fun doGetHomeInfo(): FileInfo? = doGetFileStatus(root)

  protected open fun doListStatus(rfsPageId: RfsChildrenPartId): Flow<RfsFileInfoChildren> =
    flowOfSingleInterruptible { RfsFileInfoChildren(doListStatus(rfsPageId.rfsPath)) }

  abstract fun doListStatus(path: RfsPath): List<FileInfo>?

  abstract fun doGetFileStatus(path: RfsPath): FileInfo?

  override fun isSearchSupported(): Boolean = true

  override fun list(rfsPath: RfsPath, batchId: String?, consumer: Processor<ListResult>): Future<*> {
    val folderPath = if (rfsPath.isDirectory) rfsPath else (rfsPath.parent ?: rfsPath)
    val filterPrefixName = if (rfsPath.isDirectory) null else rfsPath.name

    val listConsumer = Processor<SafeResult<RfsFileInfoChildren>> {
      val res = try {
        val files = it.resultOrThrow().fileInfos ?: emptyList()
        val filteredFiles = files.filter { it.path.name.startsWith(filterPrefixName ?: "") }
        ListResult.ofFileInfos(filteredFiles)
      }
      catch (t: Throwable) {
        ListResult.ofError(t)
      }
      consumer.process(res)
    }
    return listStatusAsync(folderPath, force = false, startFrom = batchId?.let { RfsListMarker(it) }, listConsumer)
  }

  override fun searchInConnection(query: String, batchId: String?, consumer: Processor<SearchResult>): Future<*> {
    val url = transformSearchQueryToStringPath(query)
    val path = createRfsPath(url)
    val folderPath = if (path.isDirectory) path else (path.parent ?: path)
    val filterPrefixName = if (path.isDirectory) null else path.name

    val listConsumer = Processor<SafeResult<RfsFileInfoChildren>> {
      val res = try {
        val files = it.resultOrThrow().fileInfos ?: emptyList()
        val filteredFiles = files.filter { it.path.name.startsWith(filterPrefixName ?: "") }
        SearchResult.ofFileInfos(query, filteredFiles)
      }
      catch (t: Throwable) {
        SearchResult.ofError(t)
      }
      consumer.process(res)
    }
    return listStatusAsync(folderPath, force = false, startFrom = batchId?.let { RfsListMarker(it) }, listConsumer)
  }

  open fun transformSearchQueryToStringPath(query: String) = if (query.startsWith("http"))
    UrlFileOpener.getStoragePathFromHttpWithPostBucket(query)
  else
    UrlFileOpener.getPath(query).removePrefix("/")

  final override fun mkdir(path: RfsPath): Result<Unit> = when {
    !isMkDirSupported -> Result.success(Unit)
    path.isFile -> Result.failure(DriverException(KafkaMessagesBundle.message ("rfs.error.no.mkdir.for.file")))
    else -> safeExecutor.asyncInterruptible(taskName = "Creating directory '$path'") {
      doMkdir(path)
      fileInfoManager.waitAppear(path)
    }.blockingGet().toKotlinResult()
  }

  abstract fun doMkdir(path: RfsPath)

  override fun allowSameNamedFilesAndDirectories(): Boolean = false


  override fun ensureOrCreateParentDirectory(fullPath: RfsPath, existingInfo: FileInfo?) {
    if (existingInfo != null)
      return
    val parentPath = fullPath.parent ?: return
    if (parentPath.isRoot)
      return

    val status: FileInfo? = getFileStatus(parentPath).resultOrThrow()
    if (status != null)
      return

    doMkdir(parentPath)

    repeat(6) {
      val freshStatus = getFileStatus(parentPath, force = true).resultOrThrow()
      if (freshStatus != null)
        return
      Thread.sleep(500)
    }

    throw Exception(KafkaMessagesBundle.message("cannot.receive.mkdir.result", parentPath))
  }

  protected open fun preprocessPath(path: String): String = path

  protected fun withoutLeadingSlashes(path: String): String {
    var pos = 0
    while (pos < path.length && path[pos] == '/') {
      pos++
    }
    return if (pos == path.length) "" else path.substring(pos)
  }

  protected open val allowEmptyDirName: Boolean = false

  override fun createRfsPath(path: String): RfsPath {
    if (allowEmptyDirName && path == "/")
      return RfsPath(listOf(""), true)
    val preprocessedPath = preprocessPath(path)
    //we may have lost or exposed the trailing "/"
    val isDirectory = path.endsWith("/") || preprocessedPath.endsWith("/")
    val elements = preprocessedPath.removeSuffix("/").split("/")
    return if (elements.isEmpty() || elements.size == 1 && elements.first() == "")
      root
    else
      RfsPath(elements, isDirectory)
  }

  @OptIn(ExperimentalTime::class)
  protected open suspend fun innerRefreshConnection(calledByUser: Boolean): ReadyConnectionStatus {
    try {
      val diff = measureTime {
        withContext(Dispatchers.IO) {
          doRefreshConnection(calledByUser)
        }
      }

      val restToWait = BdIdeRegistryUtil.minimalRefreshDriverTime() - diff
      if (restToWait.isPositive()) {
        delay(restToWait)
      }
      withContext(Dispatchers.IO) {
        refreshHomeUri()
      }
    }
    catch (t: Throwable) {
      return FailedConnectionStatus(t)
    }

    return withContext(Dispatchers.IO) {
      doCheckAvailable()
    }
  }

  override fun toString(): String = "${javaClass.name}(${presentableName})"

  companion object {
    private val logger = SmartLogger(this::class.java)
  }
}

fun Driver.isLocal(): Boolean = this is LocalDriver
