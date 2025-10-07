package io.confluent.intellijplugin.core.rfs.fileInfo

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import io.confluent.intellijplugin.core.rfs.driver.*
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.ErrorResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.OkResult
import io.confluent.intellijplugin.core.rfs.driver.fileinfo.SafeResult
import io.confluent.intellijplugin.core.util.SmartLogger
import io.confluent.intellijplugin.core.util.executeOnPooledThread
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.concurrent.atomic.AtomicBoolean

abstract class DriverFileInfoManager : Disposable {
    protected val isReloading = AtomicBoolean(false)
    protected val isConnecting = AtomicBoolean(false)

    abstract val driver: Driver

    open fun startFileRefresh() {}

    fun getCachedFileInfoOrReload(rfsPath: RfsPath): SafeResult<FileInfo?>? {
        val cached = driver.getCachedFileInfo(rfsPath)

        return if (cached != null)
            cached
        else {
            invokeLoadFileInfo(rfsPath)
            null
        }
    }

    abstract fun loadFileInfo(rfsPath: RfsPath, force: Boolean): SafeResult<FileInfo?>

    fun getCachedFileInfo(rfsPath: RfsPath): SafeResult<FileInfo?>? {
        val cached: SafeResult<FileInfo?>? = when (val connectionStatus = getConnectionStatus()) {
            is ConnectedConnectionStatus -> getCachedFileInfoInner(rfsPath)
            //TODO is it bad to pass actual exception here?
            is FailedConnectionStatus -> ErrorResult(connectionStatus.getException())
            is ConnectingConnectionStatus -> {
                log(rfsPath, "FileInfo will not be reload. Driver is connecting")
                OkResult(null)
            }
        }

        log(rfsPath, "Get Cache file Info for $rfsPath: $cached")
        return cached
    }

    fun getCachedChildrenOrReload(rfsPath: RfsPath): SafeResult<RfsFileInfoChildren>? {
        val cached = getCachedChildren(rfsPath)

        if (cached != null)
            return cached

        executeOnPooledThread {
            getChildren(rfsPath, force = true)
        }

        return null
    }

    @RequiresBackgroundThread
    open fun getChildren(rfsPath: RfsPath, force: Boolean = false): SafeResult<RfsFileInfoChildren> {
        return runBlockingMaybeCancellable {
            getChildren(RfsChildrenPartId(rfsPath), force).firstOrNull() ?: ErrorResult(
                IllegalStateException("listStatusAsync call was not completed")
            )
        }
    }

    abstract fun getChildren(pageKey: RfsChildrenPartId, force: Boolean = false): Flow<SafeResult<RfsFileInfoChildren>>

    protected abstract fun invokeLoadFileInfo(rfsPath: RfsPath)


    abstract fun refreshFiles(path: RfsPath)
    abstract suspend fun refreshDriver(activitySource: ActivitySource)

    abstract fun waitAppear(rfsPath: RfsPath)

    abstract fun waitDisappear(rfsPath: RfsPath)

    fun driverRefreshStarted() {
        log(null, "Start refresh driver")

        isConnecting.set(true)
        if (isReloading.get())
            notify {
                it.nodeUpdated(driver.root)
            }
        else
            notify {
                it.treeUpdated(driver.root)
            }
    }


    open fun driverRefreshFinished(status: DriverConnectionStatus) {
        log(null, "Finish refresh driver. Status: $status", error = status.getException())
        isReloading.set(false)
        isConnecting.set(false)

        runCatching {
            refreshFiles(driver.root)

            notify {
                it.driverRefreshFinished(status)
            }
        }.onFailure {
            logger.error(it)
        }
    }


    fun getConnectionStatus(): DriverConnectionStatus {
        val result = if (isConnecting.get() || isReloading.get())
            ConnectingConnectionStatus
        else
            getDriverConnectionStatus()

        log(null, "Connection status: $result")

        return result
    }

    open fun clearFileInfoCaches(paths: List<RfsPath>) {}

    protected abstract fun getCachedFileInfoInner(rfsPath: RfsPath): SafeResult<FileInfo?>?
    protected abstract fun getCachedChildrenInner(rfsPath: RfsPath): SafeResult<RfsFileInfoChildren>?
    protected abstract fun getDriverConnectionStatus(): DriverConnectionStatus

    protected suspend fun invokeDriverRefresh(activitySource: ActivitySource) {
        log(null, "Driver refresh invoked")
        isReloading.set(true)
        driver.refreshConnection(activitySource)
    }

    protected abstract fun notify(body: (DriverRfsListener) -> Unit)

    private fun getCachedChildren(rfsPath: RfsPath): SafeResult<RfsFileInfoChildren>? {
        val cached = when (val connectionStatus = getConnectionStatus()) {
            is ConnectedConnectionStatus -> getCachedChildrenInner(rfsPath)
            //TODO is it bad to pass actual exception here?
            is FailedConnectionStatus -> ErrorResult(connectionStatus.getException())
            is ConnectingConnectionStatus -> {
                log(rfsPath, "Children will not be reloaded. Driver is connecting")
                OkResult(RfsFileInfoChildren(null))
            }
        }

        log(rfsPath, "Get Cache children: $cached")
        return cached
    }

    protected fun log(rfsPath: RfsPath, msg: String, error: Throwable? = null) =
        log(RfsChildrenPartId(rfsPath), msg, error)

    @Suppress("DuplicatedCode")
    protected fun log(pageKey: RfsChildrenPartId?, msg: String, error: Throwable? = null) {
        val pathPrefix = pageKey?.let { "${it.rfsPath}, page ${it.markerId} - " } ?: ""
        val infoMsg = "$pathPrefix$msg. Driver:${driver.presentableName}"
        if (ApplicationManager.getApplication().isUnitTestMode) {
            //println(this::class.java.simpleName + ": " + infoMsg)
            logger.info(infoMsg, error)
        } else {
            //println(this::class.java.simpleName + ": " + infoMsg)
            logger.debug(infoMsg, error)
        }
    }

    companion object {
        private val logger = SmartLogger(this::class.java)
    }
}