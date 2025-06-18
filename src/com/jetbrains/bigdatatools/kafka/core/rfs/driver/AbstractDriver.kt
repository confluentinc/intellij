package com.jetbrains.bigdatatools.kafka.core.rfs.driver

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.util.Processor
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.depend.MasterDriver
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.fileinfo.SafeResult
import com.jetbrains.bigdatatools.kafka.core.rfs.driver.manager.DriverManager
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.DriverRfsListener
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsFileInfoChildren
import com.jetbrains.bigdatatools.kafka.core.rfs.fileInfo.RfsListMarker
import com.jetbrains.bigdatatools.kafka.core.util.SmartLogger
import com.jetbrains.bigdatatools.kafka.core.util.async.DeferredJobHandle
import java.util.concurrent.CompletableFuture

abstract class AbstractDriver : Driver {
  /**
   * null for application-wide drivers
   */
  abstract val project: Project?

  override val safeExecutor: SafeExecutor by lazy {
    SafeExecutor.createInstance(this, "Driver $presentableName", timeout)
  }

  private var listeners: List<DriverRfsListener> = emptyList()
  override fun addListener(listener: DriverRfsListener) {
    listeners = listeners + listener
  }

  override fun removeListener(listener: DriverRfsListener) {
    listeners = listeners - listener
  }

  fun notify(body: (DriverRfsListener) -> Unit) = listeners.forEach {
    try {
      body(it)
    }
    catch (t: Throwable) {
      logger.error(t)
    }
  }

  val refreshProcess by lazy {
    DeferredJobHandle<ReadyConnectionStatus>(safeExecutor.coroutineScope)
  }

  override fun listStatusAsync(path: RfsPath,
                               force: Boolean,
                               startFrom: RfsListMarker?,
                               resultProcessor: Processor<SafeResult<RfsFileInfoChildren>>): CompletableFuture<*> {
    if (startFrom != null) throw UnsupportedOperationException("pagination is not supported for driver $this")
    return CompletableFuture<Unit>().also {
      ApplicationManager.getApplication().executeOnPooledThread {
        while (fileInfoManager.getConnectionStatus().isConnecting()) {
          Thread.sleep(200)
        }
        val result = listStatus(path, force).map { it.copy(nextMarker = it.nextMarker?.copy(marker = null)) }
        resultProcessor.process(result)
        it.complete(Unit)
      }
    }
  }

  protected fun prepareSlaveDriver() {
    val sourceConnectionId = connectionData.sourceConnection
    if (sourceConnectionId != null) {
      val driver = DriverManager.getDriverById(project, sourceConnectionId) ?: return
      val masterDriver = driver as MasterDriver
      masterDriver.prepareRefreshDependedDriver(this)
    }
  }

  companion object {
    private val logger = SmartLogger(this::class.java)
  }
}